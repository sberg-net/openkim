/*
 * Copyright 2023 sberg it-systeme GmbH
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package net.sberg.openkim.log.error;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class MailaddressRcptToErrorContext implements IErrorContext {
    private Map<String, EnumErrorCode> addressErrors = new HashMap<>();
    private List<String> rcptAddresses = new ArrayList<>();

    public boolean isEmpty() {
        return addressErrors.isEmpty() && rcptAddresses.isEmpty();
    }

    public void add(String rcptAddress, EnumErrorCode errorCode) {
        if (!addressErrors.containsKey(rcptAddress)) {
            addressErrors.put(rcptAddress, errorCode);
        }
        if (!rcptAddresses.contains(rcptAddress)) {
            rcptAddresses.add(rcptAddress);
        }
    }
    public boolean isError(String address) {
        return addressErrors.containsKey(address);
    }
}
