/*
 * Copyright 2015 Data Archiving and Networked Services (an institute of
 * Koninklijke Nederlandse Akademie van Wetenschappen), King's College London,
 * Georg-August-Universitaet Goettingen Stiftung Oeffentlichen Rechts
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.ehri.extension.test;

import com.sun.jersey.api.client.ClientResponse;
import eu.ehri.project.definitions.Entities;
import org.junit.Test;

import static com.sun.jersey.api.client.ClientResponse.Status.CREATED;
import static com.sun.jersey.api.client.ClientResponse.Status.OK;

public class LinkRestClientTest extends BaseRestClientTest {

    @Test
    public void testGetLinks() throws Exception {
        // Fetch annotations for an item.
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.LINK, "for", "c1"))
                .get(ClientResponse.class);
        assertStatus(OK, response);
    }

    @Test
    public void testDeleteAccessPoint() throws Exception {
        // Create a link annotation between two objects
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.LINK, "accessPoint", "ur1"))
                .delete(ClientResponse.class);
        assertStatus(OK, response);
    }

    @Test
    public void testCreateLink() throws Exception {
        // Create a link annotation between two objects
        String jsonLinkTestString = "{\"type\": \"Link\", \"data\":{\"identifier\": \"39dj28dhs\", " +
                "\"body\": \"test\", \"type\": \"associate\"}}";
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.LINK, "c1", "c4")).entity(jsonLinkTestString)
                .post(ClientResponse.class);
        assertStatus(CREATED, response);
    }
}
