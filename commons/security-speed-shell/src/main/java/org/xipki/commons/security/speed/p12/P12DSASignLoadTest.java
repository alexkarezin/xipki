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

package org.xipki.commons.security.speed.p12;

import java.security.SecureRandom;

import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.api.SecurityFactory;
import org.xipki.commons.security.api.p12.P12KeypairGenerationResult;
import org.xipki.commons.security.api.p12.P12KeypairGenerator;
import org.xipki.commons.security.api.p12.P12KeystoreGenerationParameters;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class P12DSASignLoadTest extends P12SignLoadTest {

    public P12DSASignLoadTest(
            final P12KeypairGenerator p12KeypairGenerator,
            final SecurityFactory securityFactory,
            final String signatureAlgorithm,
            final int pLength,
            final int qLength)
    throws Exception {
        super(securityFactory, signatureAlgorithm,
                generateKeystore(p12KeypairGenerator, pLength, qLength),
                "PKCS#12 DSA signature creation\n"
                    + "pLength: " + pLength + "\n"
                    + "qLength: " + qLength);
    }

    private static byte[] generateKeystore(
            final P12KeypairGenerator p12KeypairGenerator,
            final int pLength,
            final int qLength)
    throws Exception {
        byte[] keystoreBytes = getPrecomputedDSAKeystore(pLength, qLength);
        if (keystoreBytes == null) {
            ParamUtil.requireNonNull("p12KeypairGenerator", p12KeypairGenerator);
            P12KeystoreGenerationParameters params = new P12KeystoreGenerationParameters(
                    PASSWORD.toCharArray());
            params.setRandom(new SecureRandom());
            P12KeypairGenerationResult identity = p12KeypairGenerator.generateDSAKeypair(pLength,
                    qLength, params);
            keystoreBytes = identity.getKeystore();
        }
        return keystoreBytes;
    }

}
