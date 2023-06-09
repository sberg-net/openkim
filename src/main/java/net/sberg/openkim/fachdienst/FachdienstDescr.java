/*
 * Copyright 2022 sberg it-systeme GmbH
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
package net.sberg.openkim.fachdienst;

import lombok.Data;
import net.sberg.openkim.konfiguration.EnumTIEnvironment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
public class FachdienstDescr {
    private EnumFachdienst id;
    private List<FachdienstDomainDescr> services;

    public List<FachdienstDomainDescr> extractDomainDescr(EnumFachdienstDomainDescrId id, EnumTIEnvironment env) {
        List<FachdienstDomainDescr> result = new ArrayList<>();
        for (Iterator<FachdienstDomainDescr> iterator = services.iterator(); iterator.hasNext(); ) {
            FachdienstDomainDescr fachdienstDomainDescr = iterator.next();
            if (fachdienstDomainDescr.getEnv().equals(env) && fachdienstDomainDescr.getId().equals(id)) {
                result.add(fachdienstDomainDescr);
            }
        }
        return result;
    }
}
