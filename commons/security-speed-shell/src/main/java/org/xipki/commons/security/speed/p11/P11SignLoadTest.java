/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2013 - 2016 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License (version 3
 * or later at your option) as published by the Free Software Foundation
 * with the addition of the following permission added to Section 15 as
 * permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.commons.security.speed.p11;

import java.security.cert.X509Certificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.common.LoadExecutor;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.api.ConcurrentContentSigner;
import org.xipki.commons.security.api.SecurityFactory;
import org.xipki.commons.security.api.SignerException;
import org.xipki.commons.security.api.p11.P11KeyIdentifier;
import org.xipki.commons.security.api.p11.P11WritableSlot;
import org.xipki.commons.security.api.util.SignerConfUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public abstract class P11SignLoadTest extends LoadExecutor {

    class Testor implements Runnable {

        final byte[] data = new byte[] {1, 2, 3, 4};

        @Override
        public void run() {
            while (!stop() && getErrorAccout() < 1) {
                try {
                    signer.sign(data);
                    account(1, 0);
                } catch (Exception ex) {
                    account(1, 1);
                }
            }

            close();
        }

    } // class Testor

    private static final Logger LOG = LoggerFactory.getLogger(P11SignLoadTest.class);

    private final P11WritableSlot slot;

    private final ConcurrentContentSigner signer;

    private final P11KeyIdentifier keyId;

    public P11SignLoadTest(
            final SecurityFactory securityFactory,
            final P11WritableSlot slot,
            final String signatureAlgorithm,
            final P11KeyIdentifier keyId,
            final String description)
    throws SignerException {
        super(description + "\nsignature algorithm: " + signatureAlgorithm);

        ParamUtil.requireNonNull("securityFactory", securityFactory);
        ParamUtil.requireNonNull("slot", slot);
        ParamUtil.requireNonBlank("signatureAlgorithm", signatureAlgorithm);
        ParamUtil.requireNonNull("keyId", keyId);

        this.slot = slot;
        this.keyId = keyId;

        String signerConf = SignerConfUtil.getPkcs11SignerConf(
                slot.getModuleName(), slot.getSlotIdentifier(), keyId, signatureAlgorithm, 20);
        this.signer = securityFactory.createSigner("PKCS11", signerConf, (X509Certificate) null);

    }

    private void close() {
        try {
            slot.removeKeyAndCerts(keyId);
        } catch (Exception ex) {
            LOG.error("could not delete PKCS#11 key {}", keyId);
        }
    }

    @Override
    protected Runnable getTestor()
    throws Exception {
        return new Testor();
    }

}
