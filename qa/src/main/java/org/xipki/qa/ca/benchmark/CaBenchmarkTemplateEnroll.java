/*
 *
 * Copyright (c) 2013 - 2018 Lijun Liao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xipki.qa.ca.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.SchemaFactory;

import org.bouncycastle.asn1.crmf.CertRequest;
import org.bouncycastle.asn1.crmf.CertTemplate;
import org.bouncycastle.asn1.crmf.CertTemplateBuilder;
import org.bouncycastle.asn1.crmf.ProofOfPossession;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.casdk.cmp.CmpCaSdk;
import org.xipki.casdk.cmp.CmpCaSdkException;
import org.xipki.casdk.cmp.EnrollCertRequest;
import org.xipki.casdk.cmp.EnrollCertRequest.EnrollType;
import org.xipki.casdk.cmp.EnrollCertResult;
import org.xipki.casdk.cmp.EnrollCertResult.CertifiedKeyPairOrError;
import org.xipki.casdk.cmp.PkiErrorException;
import org.xipki.qa.ca.benchmark.jaxb.EnrollCertType;
import org.xipki.qa.ca.benchmark.jaxb.EnrollTemplateType;
import org.xipki.util.Args;
import org.xipki.util.BenchmarkExecutor;
import org.xipki.util.InvalidConfException;
import org.xipki.util.XmlUtil;
import org.xml.sax.SAXException;

/**
 * TODO.
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CaBenchmarkTemplateEnroll extends BenchmarkExecutor {

  private static final class CertRequestWithProfile {

    private final String certprofile;

    private final CertRequest certRequest;

    CertRequestWithProfile(String certprofile, CertRequest certRequest) {
      this.certprofile = certprofile;
      this.certRequest = certRequest;
    }

  } // class CertRequestWithProfile

  class Testor implements Runnable {

    @Override
    public void run() {
      while (!stop() && getErrorAccout() < 1) {
        Map<Integer, CertRequestWithProfile> certReqs = nextCertRequests();
        if (certReqs == null) {
          break;
        }

        boolean successful = testNext(certReqs);
        int numFailed = successful ? 0 : 1;
        account(1, numFailed);
      }
    }

    private boolean testNext(Map<Integer, CertRequestWithProfile> certRequests) {
      EnrollCertResult result;
      try {
        EnrollCertRequest request = new EnrollCertRequest(EnrollType.CERT_REQ);
        for (Integer certId : certRequests.keySet()) {
          CertRequestWithProfile certRequest = certRequests.get(certId);
          EnrollCertRequest.Entry requestEntry = new EnrollCertRequest.Entry("id-" + certId,
                  certRequest.certprofile, certRequest.certRequest, RA_VERIFIED);
          request.addRequestEntry(requestEntry);
        }

        result = caSdk.enrollCerts(null, request, null);
      } catch (CmpCaSdkException | PkiErrorException ex) {
        LOG.warn("{}: {}", ex.getClass().getName(), ex.getMessage());
        return false;
      } catch (Throwable th) {
        LOG.warn("{}: {}", th.getClass().getName(), th.getMessage());
        return false;
      }

      if (result == null) {
        return false;
      }

      Set<String> ids = result.getAllIds();
      if (ids.size() < certRequests.size()) {
        return false;
      }

      for (String id : ids) {
        CertifiedKeyPairOrError certOrError = result.getCertOrError(id);
        X509Certificate cert = (X509Certificate) certOrError.getCertificate();

        if (cert == null) {
          return false;
        }
      }

      return true;
    } // method testNext

  } // class Testor

  private static final Logger LOG = LoggerFactory.getLogger(CaBenchmarkTemplateEnroll.class);

  private static final ProofOfPossession RA_VERIFIED = new ProofOfPossession();

  private static Object jaxbUnmarshallerLock = new Object();

  private static Unmarshaller jaxbUnmarshaller;

  private final CmpCaSdk caSdk;

  private final List<BenchmarkEntry> benchmarkEntries;

  private final int num;

  private final int maxRequests;

  private AtomicInteger processedRequests = new AtomicInteger(0);

  private final AtomicLong index;

  public CaBenchmarkTemplateEnroll(CmpCaSdk caSdk, EnrollTemplateType template,
      int maxRequests, String description) throws Exception {
    super(description);

    Args.notNull(template, "template");
    this.maxRequests = maxRequests;
    this.caSdk = Args.notNull(caSdk, "caSdk");

    Calendar baseTime = Calendar.getInstance(Locale.UK);
    baseTime.set(Calendar.YEAR, 2014);
    baseTime.set(Calendar.MONTH, 0);
    baseTime.set(Calendar.DAY_OF_MONTH, 1);

    this.index = new AtomicLong(getSecureIndex());

    List<EnrollCertType> list = template.getEnrollCert();
    benchmarkEntries = new ArrayList<>(list.size());

    for (EnrollCertType entry : list) {
      KeyEntry keyEntry;
      if (entry.getEcKey() != null) {
        keyEntry = new KeyEntry.ECKeyEntry(entry.getEcKey().getCurve());
      } else if (entry.getRsaKey() != null) {
        keyEntry = new KeyEntry.RSAKeyEntry(entry.getRsaKey().getModulusLength());
      } else if (entry.getDsaKey() != null) {
        keyEntry = new KeyEntry.DSAKeyEntry(entry.getDsaKey().getPLength());
      } else {
        throw new IllegalStateException("should not reach here, unknown child of KeyEntry");
      }

      String randomDnStr = entry.getRandomDn();
      BenchmarkEntry.RandomDn randomDn = BenchmarkEntry.RandomDn.getInstance(randomDnStr);
      if (randomDn == null) {
        throw new InvalidConfException("invalid randomDn " + randomDnStr);
      }

      benchmarkEntries.add(
          new BenchmarkEntry(entry.getCertprofile(), keyEntry, entry.getSubject(), randomDn));
    }

    num = benchmarkEntries.size();
  } // constructor

  @Override
  protected int getRealAccount(int account) {
    return num * account;
  }

  @Override
  protected Runnable getTestor() throws Exception {
    return new Testor();
  }

  public int getNumberOfCertsInOneRequest() {
    return benchmarkEntries.size();
  }

  private Map<Integer, CertRequestWithProfile> nextCertRequests() {
    if (maxRequests > 0) {
      int num = processedRequests.getAndAdd(1);
      if (num >= maxRequests) {
        return null;
      }
    }

    Map<Integer, CertRequestWithProfile> certRequests = new HashMap<>();
    final int n = benchmarkEntries.size();
    for (int i = 0; i < n; i++) {
      BenchmarkEntry benchmarkEntry = benchmarkEntries.get(i);
      final int certId = i + 1;
      CertTemplateBuilder certTempBuilder = new CertTemplateBuilder();

      long thisIndex = index.getAndIncrement();
      certTempBuilder.setSubject(benchmarkEntry.getX500Name(thisIndex));

      SubjectPublicKeyInfo spki = benchmarkEntry.getSubjectPublicKeyInfo();
      certTempBuilder.setPublicKey(spki);

      CertTemplate certTemplate = certTempBuilder.build();
      CertRequest certRequest = new CertRequest(certId, certTemplate, null);
      CertRequestWithProfile requestWithCertprofile = new CertRequestWithProfile(
              benchmarkEntry.getCertprofile(), certRequest);
      certRequests.put(certId, requestWithCertprofile);
    }
    return certRequests;
  } // method nextCertRequests

  public static EnrollTemplateType parse(InputStream configStream) throws InvalidConfException {
    Args.notNull(configStream, "configStream");
    Object root;

    synchronized (jaxbUnmarshallerLock) {
      try {
        if (jaxbUnmarshaller == null) {
          JAXBContext context = JAXBContext.newInstance(
              org.xipki.qa.ca.benchmark.jaxb.ObjectFactory.class);
          jaxbUnmarshaller = context.createUnmarshaller();

          final SchemaFactory schemaFact = SchemaFactory.newInstance(
              javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
          URL url = org.xipki.qa.ca.benchmark.jaxb.ObjectFactory.class
                      .getResource("/xsd/benchmark.xsd");
          jaxbUnmarshaller.setSchema(schemaFact.newSchema(url));
        }

        root = jaxbUnmarshaller.unmarshal(configStream);
      } catch (SAXException ex) {
        throw new InvalidConfException("parsing profile failed, message: " + ex.getMessage(), ex);
      } catch (JAXBException ex) {
        throw new InvalidConfException("parsing profile failed, message: " + XmlUtil.getMessage(ex),
            ex);
      }
    }

    try {
      configStream.close();
    } catch (IOException ex) {
      LOG.warn("could not close xmlConfStream: {}", ex.getMessage());
    }

    if (root instanceof JAXBElement) {
      return (EnrollTemplateType) ((JAXBElement<?>) root).getValue();
    } else {
      throw new InvalidConfException("invalid root element type");
    }
  } // method parse

}
