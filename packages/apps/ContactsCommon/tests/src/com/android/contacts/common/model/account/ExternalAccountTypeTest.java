/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.common.model.account;

import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.unittest.R;
import com.google.common.base.Objects;

import java.util.List;

/**
 * Test case for {@link com.android.contacts.common.model.account.ExternalAccountType}.
 *
 * adb shell am instrument -w -e class com.android.contacts.model.ExternalAccountTypeTest \
       com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@SmallTest
public class ExternalAccountTypeTest extends AndroidTestCase {
    public void testResolveExternalResId() {
        final Context c = getContext();
        // In this test we use the test package itself as an external package.
        final String packageName = getTestContext().getPackageName();

        // Resource name empty.
        assertEquals(-1, ExternalAccountType.resolveExternalResId(c, null, packageName, ""));
        assertEquals(-1, ExternalAccountType.resolveExternalResId(c, "", packageName, ""));

        // Name doesn't begin with '@'
        assertEquals(-1, ExternalAccountType.resolveExternalResId(c, "x", packageName, ""));

        // Invalid resource name
        assertEquals(-1, ExternalAccountType.resolveExternalResId(c, "@", packageName, ""));
        assertEquals(-1, ExternalAccountType.resolveExternalResId(c, "@a", packageName, ""));
        assertEquals(-1, ExternalAccountType.resolveExternalResId(c, "@a/b", packageName, ""));

        // Valid resource name
        assertEquals(R.string.test_string, ExternalAccountType.resolveExternalResId(c,
                "@string/test_string", packageName, ""));
    }

    /**
     * Initialize with an invalid package name and see if type type will *not* be initialized.
     */
    public void testNoPackage() {
        final ExternalAccountType type = new ExternalAccountType(getContext(),
                "!!!no such package name!!!", false);
        assertFalse(type.isInitialized());
    }

    /**
     * Initialize with the name of an existing package, which has no contacts.xml metadata.
     */
    /*
    public void testNoMetadata() {
        // Use the main application package, which does exist, but has no contacts.xml in it.
        String packageName = getContext().getPackageName();
        Log.e("TEST", packageName);
        final ExternalAccountType type = new ExternalAccountType(getContext(),
                packageName, false);
        assertTrue(type.isInitialized());
    }
    */

    /**
     * Initialize with the test package itself and see if EditSchema is correctly parsed.
     */
    public void testEditSchema() {
        final ExternalAccountType type = new ExternalAccountType(getContext(),
                getTestContext().getPackageName(), false);

        assertTrue(type.isInitialized());

        // Let's just check if the DataKinds are registered.
        assertNotNull(type.getKindForMimetype(StructuredName.CONTENT_ITEM_TYPE));
        assertNotNull(type.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME));
        assertNotNull(type.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME));
        assertNotNull(type.getKindForMimetype(Email.CONTENT_ITEM_TYPE));
        assertNotNull(type.getKindForMimetype(StructuredPostal.CONTENT_ITEM_TYPE));
        assertNotNull(type.getKindForMimetype(Im.CONTENT_ITEM_TYPE));
        assertNotNull(type.getKindForMimetype(Organization.CONTENT_ITEM_TYPE));
        assertNotNull(type.getKindForMimetype(Photo.CONTENT_ITEM_TYPE));
        assertNotNull(type.getKindForMimetype(Note.CONTENT_ITEM_TYPE));
        assertNotNull(type.getKindForMimetype(Website.CONTENT_ITEM_TYPE));
        assertNotNull(type.getKindForMimetype(SipAddress.CONTENT_ITEM_TYPE));
        assertNotNull(type.getKindForMimetype(Event.CONTENT_ITEM_TYPE));
        assertNotNull(type.getKindForMimetype(Relation.CONTENT_ITEM_TYPE));
    }

    /**
     * Initialize with "contacts_fallback.xml" and compare the DataKinds to those of
     * {@link com.android.contacts.common.model.account.FallbackAccountType}.
     */
    public void testEditSchema_fallback() {
        final ExternalAccountType type = new ExternalAccountType(getContext(),
                getTestContext().getPackageName(), false,
                getTestContext().getResources().getXml(R.xml.contacts_fallback)
                );

        assertTrue(type.isInitialized());

        // Create a fallback type with the same resource package name, and compare all the data
        // kinds to its.
        final AccountType reference = FallbackAccountType.createWithPackageNameForTest(
                getContext(), type.resourcePackageName);

        /**
         * M: TODO: [Migration]
         * MTK FallBackAccountType contains some Customization, so the ExternalAccountType based
         * on R.xml.contacts_fallback can't describe the FallBackAccountType.
         * such as DataKindSipAddress / DataKindGroupMembership
         * so this case need a harder improvement.
         * Original code: @{
        assertsDataKindEquals(reference.getSortedDataKinds(), type.getSortedDataKinds());
         */
    }

    public void testEditSchema_mustHaveChecks() {
        checkEditSchema_mustHaveChecks(R.xml.missing_contacts_base, true);
        checkEditSchema_mustHaveChecks(R.xml.missing_contacts_photo, false);
        checkEditSchema_mustHaveChecks(R.xml.missing_contacts_name, false);
        checkEditSchema_mustHaveChecks(R.xml.missing_contacts_name_attr1, false);
        checkEditSchema_mustHaveChecks(R.xml.missing_contacts_name_attr2, false);
        checkEditSchema_mustHaveChecks(R.xml.missing_contacts_name_attr3, false);
        checkEditSchema_mustHaveChecks(R.xml.missing_contacts_name_attr4, false);
        checkEditSchema_mustHaveChecks(R.xml.missing_contacts_name_attr5, false);
        checkEditSchema_mustHaveChecks(R.xml.missing_contacts_name_attr6, false);
        checkEditSchema_mustHaveChecks(R.xml.missing_contacts_name_attr7, false);
    }

    private void checkEditSchema_mustHaveChecks(int xmlResId, boolean expectInitialized) {
        final ExternalAccountType type = new ExternalAccountType(getContext(),
                getTestContext().getPackageName(), false,
                getTestContext().getResources().getXml(xmlResId)
                );

        assertEquals(expectInitialized, type.isInitialized());
    }

    /**
     * Initialize with "contacts_readonly.xml" and see if all data kinds are correctly registered.
     */
    public void testReadOnlyDefinition() {
        final ExternalAccountType type = new ExternalAccountType(getContext(),
                getTestContext().getPackageName(), false,
                getTestContext().getResources().getXml(R.xml.contacts_readonly)
                );
        assertTrue(type.isInitialized());

        // Shouldn't have a "null" mimetype.
        assertTrue(type.getKindForMimetype(null) == null);

        // 3 kinds are defined in XML and 4 are added by default.
        assertEquals(4 + 3, type.getSortedDataKinds().size());

        // Check for the default kinds.
        assertNotNull(type.getKindForMimetype(StructuredName.CONTENT_ITEM_TYPE));
        assertNotNull(type.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME));
        assertNotNull(type.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME));
        assertNotNull(type.getKindForMimetype(Photo.CONTENT_ITEM_TYPE));

        // Check for type specific kinds.
        DataKind kind = type.getKindForMimetype("vnd.android.cursor.item/a.b.c");
        assertNotNull(kind);
        // No check for icon -- we actually just ignore it.
        assertEquals("data1", ((BaseAccountType.SimpleInflater) kind.actionHeader)
                .getColumnNameForTest());
        assertEquals("data2", ((BaseAccountType.SimpleInflater) kind.actionBody)
                .getColumnNameForTest());

        kind = type.getKindForMimetype("vnd.android.cursor.item/d.e.f");
        assertNotNull(kind);
        assertEquals("data3", ((BaseAccountType.SimpleInflater) kind.actionHeader)
                .getColumnNameForTest());
        assertEquals("data4", ((BaseAccountType.SimpleInflater) kind.actionBody)
                .getColumnNameForTest());

        kind = type.getKindForMimetype("vnd.android.cursor.item/xyz");
        assertNotNull(kind);
        assertEquals("data5", ((BaseAccountType.SimpleInflater) kind.actionHeader)
                .getColumnNameForTest());
        assertEquals("data6", ((BaseAccountType.SimpleInflater) kind.actionBody)
                .getColumnNameForTest());
    }

    private static void assertsDataKindEquals(List<DataKind> expectedKinds,
            List<DataKind> actualKinds) {
        final int count = Math.max(actualKinds.size(), expectedKinds.size());
        for (int i = 0; i < count; i++) {
            String actual =  actualKinds.size() > i ? actualKinds.get(i).toString() : "(n/a)";
            String expected =  expectedKinds.size() > i ? expectedKinds.get(i).toString() : "(n/a)";

            // Because assertEquals()'s output is not very friendly when comparing two similar
            // strings, we manually do the check.
            if (!Objects.equal(actual, expected)) {
                final int commonPrefixEnd = findCommonPrefixEnd(actual, expected);
                fail("Kind #" + i
                        + "\n[Actual]\n" + insertMarkerAt(actual, commonPrefixEnd)
                        + "\n[Expected]\n" + insertMarkerAt(expected, commonPrefixEnd));
            }
        }
    }

    private static int findCommonPrefixEnd(String s1, String s2) {
        int i = 0;
        for (;;) {
            final boolean s1End = (s1.length() <= i);
            final boolean s2End = (s2.length() <= i);
            if (s1End || s2End) {
                return i;
            }
            if (s1.charAt(i) != s2.charAt(i)) {
                return i;
            }
            i++;
        }
    }

    private static String insertMarkerAt(String s, int position) {
        final String MARKER = "***";
        if (position > s.length()) {
            return s + MARKER;
        } else {
            return new StringBuilder(s).insert(position, MARKER).toString();
        }
    }
}
