/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.telecom;

import android.annotation.SystemApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.lang.String;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Objects;

/**
 * Represents a distinct method to place or receive a phone call. Apps which can place calls and
 * want those calls to be integrated into the dialer and in-call UI should build an instance of
 * this class and register it with the system using {@link TelecomManager#registerPhoneAccount}.
 * <p>
 * {@link TelecomManager} uses registered {@link PhoneAccount}s to present the user with
 * alternative options when placing a phone call. When building a {@link PhoneAccount}, the app
 * should supply a valid {@link PhoneAccountHandle} that references the {@link ConnectionService}
 * implementation Telecom will use to interact with the app.
 * @hide
 */
@SystemApi
public class PhoneAccount implements Parcelable {

    /**
     * Flag indicating that this {@code PhoneAccount} can act as a connection manager for
     * other connections. The {@link ConnectionService} associated with this {@code PhoneAccount}
     * will be allowed to manage phone calls including using its own proprietary phone-call
     * implementation (like VoIP calling) to make calls instead of the telephony stack.
     * <p>
     * When a user opts to place a call using the SIM-based telephony stack, the
     * {@link ConnectionService} associated with this {@code PhoneAccount} will be attempted first
     * if the user has explicitly selected it to be used as the default connection manager.
     * <p>
     * See {@link #getCapabilities}
     */
    public static final int CAPABILITY_CONNECTION_MANAGER = 0x1;

    /**
     * Flag indicating that this {@code PhoneAccount} can make phone calls in place of
     * traditional SIM-based telephony calls. This account will be treated as a distinct method
     * for placing calls alongside the traditional SIM-based telephony stack. This flag is
     * distinct from {@link #CAPABILITY_CONNECTION_MANAGER} in that it is not allowed to manage
     * or place calls from the built-in telephony stack.
     * <p>
     * See {@link #getCapabilities}
     * <p>
     * {@hide}
     */
    public static final int CAPABILITY_CALL_PROVIDER = 0x2;

    /**
     * Flag indicating that this {@code PhoneAccount} represents a built-in PSTN SIM
     * subscription.
     * <p>
     * Only the Android framework can register a {@code PhoneAccount} having this capability.
     * <p>
     * See {@link #getCapabilities}
     */
    public static final int CAPABILITY_SIM_SUBSCRIPTION = 0x4;

    /**
     * Flag indicating that this {@code PhoneAccount} is capable of placing video calls.
     * <p>
     * See {@link #getCapabilities}
     * @hide
     */
    public static final int CAPABILITY_VIDEO_CALLING = 0x8;

    /**
     * Flag indicating that this {@code PhoneAccount} is capable of placing emergency calls.
     * By default all PSTN {@code PhoneAccount}s are capable of placing emergency calls.
     * <p>
     * See {@link #getCapabilities}
     */
    public static final int CAPABILITY_PLACE_EMERGENCY_CALLS = 0x10;

    /**
     * Flag indicating that this {@code PhoneAccount} is capable of being used by all users. This
     * should only be used by system apps (and will be ignored for all other apps trying to use it).
     * <p>
     * See {@link #getCapabilities}
     * @hide
     */
    public static final int CAPABILITY_MULTI_USER = 0x20;

    /**
     * Flag indicating that this {@code PhoneAccount} is capable of placing volte calls.
     * This flag will be set only when the IMS service camped on the IMS server, which
     * means it is possible to try placing an volte call at this time.
     * <p>
     * See {@link #getCapabilities()}
     * @hide
     * @internal
     */
    public static final int CAPABILITY_VOLTE_CALLING = CAPABILITY_MULTI_USER << 1;

    /**
     * M: Flag indicating that this {@code PhoneAccount} is capable of placing volte enhanced
     * conference call. This flag will be set only when the IMS service camped on the IMS
     * server and with a sim inserted which can placing an volte conference call.
     * <p>
     * See {@link #getCapabilities()}
     * @hide
     */
    public static final int CAPABILITY_VOLTE_ENHANCED_CONFERENCE = CAPABILITY_VOLTE_CALLING << 1;

    /**
     * M: Flag indicating that this {@code PhoneAccount} is capable of placing a call via CDMA
     * network. This flag will be set only when the phone for this account is CDMA type.
     * <p>
     * See {@link #getCapabilities()}
     * @hide
     */
    public static final int CAPABILITY_CDMA_CALL_PROVIDER = CAPABILITY_VOLTE_ENHANCED_CONFERENCE << 1;

    /**
     * Flag indicating that this {@code PhoneAccount} is capable of placing wifi calls.
     * This flag will be set only when the IMS service broadcast the wifi calling enabled broadcast,
     * which means it is possible to dial wifi call at this time.
     * <p>
     * See {@link #getCapabilities()}
     * @hide
     */
    public static final int CAPABILITY_WIFI_CALLING = CAPABILITY_CDMA_CALL_PROVIDER << 1;
    /**
     * M: Flag indicating that this {@code PhoneAccount} is not available for
     * placing out a voice call.
     * <p>
     * See {@link #getCapabilities()}
     * @hide
     */
    public static final int CAPABILITY_UNAVAILABLE_FOR_CALL = CAPABILITY_WIFI_CALLING << 1;

    /**
     * URI scheme for telephone number URIs.
     */
    public static final String SCHEME_TEL = "tel";

    /**
     * URI scheme for voicemail URIs.
     */
    public static final String SCHEME_VOICEMAIL = "voicemail";

    /**
     * URI scheme for SIP URIs.
     */
    public static final String SCHEME_SIP = "sip";

    /**
     * Indicating no icon tint is set.
     */
    public static final int NO_ICON_TINT = 0;

    /**
     * Indicating no hightlight color is set.
     */
    public static final int NO_HIGHLIGHT_COLOR = 0;

    /**
     * Indicating no resource ID is set.
     */
    public static final int NO_RESOURCE_ID = -1;

    private final PhoneAccountHandle mAccountHandle;
    private final Uri mAddress;
    private final Uri mSubscriptionAddress;
    private final int mCapabilities;
    private final int mIconResId;
    private final String mIconPackageName;
    private final Bitmap mIconBitmap;
    private final int mIconTint;
    private final int mHighlightColor;
    private final CharSequence mLabel;
    private final CharSequence mShortDescription;
    private final List<String> mSupportedUriSchemes;

    /**
     * Helper class for creating a {@link PhoneAccount}.
     */
    public static class Builder {
        private PhoneAccountHandle mAccountHandle;
        private Uri mAddress;
        private Uri mSubscriptionAddress;
        private int mCapabilities;
        private int mIconResId;
        private String mIconPackageName;
        private Bitmap mIconBitmap;
        private int mIconTint = NO_ICON_TINT;
        private int mHighlightColor = NO_HIGHLIGHT_COLOR;
        private CharSequence mLabel;
        private CharSequence mShortDescription;
        private List<String> mSupportedUriSchemes = new ArrayList<String>();

        /**
         * Creates a builder with the specified {@link PhoneAccountHandle} and label.
         */
        public Builder(PhoneAccountHandle accountHandle, CharSequence label) {
            this.mAccountHandle = accountHandle;
            this.mLabel = label;
        }

        /**
         * Creates an instance of the {@link PhoneAccount.Builder} from an existing
         * {@link PhoneAccount}.
         *
         * @param phoneAccount The {@link PhoneAccount} used to initialize the builder.
         */
        public Builder(PhoneAccount phoneAccount) {
            mAccountHandle = phoneAccount.getAccountHandle();
            mAddress = phoneAccount.getAddress();
            mSubscriptionAddress = phoneAccount.getSubscriptionAddress();
            mCapabilities = phoneAccount.getCapabilities();
            mIconResId = phoneAccount.getIconResId();
            mIconPackageName = phoneAccount.getIconPackageName();
            mIconBitmap = phoneAccount.getIconBitmap();
            mIconTint = phoneAccount.getIconTint();
            mHighlightColor = phoneAccount.getHighlightColor();
            mLabel = phoneAccount.getLabel();
            mShortDescription = phoneAccount.getShortDescription();
            mSupportedUriSchemes.addAll(phoneAccount.getSupportedUriSchemes());
        }

        /** @hide */
        public Builder setAccountHandle(PhoneAccountHandle accountHandle) {
            mAccountHandle = accountHandle;
            return this;
        }

        /**
         * Sets the address. See {@link PhoneAccount#getAddress}.
         *
         * @param value The address of the phone account.
         * @return The builder.
         */
        public Builder setAddress(Uri value) {
            this.mAddress = value;
            return this;
        }

        /**
         * Sets the subscription address. See {@link PhoneAccount#getSubscriptionAddress}.
         *
         * @param value The subscription address.
         * @return The builder.
         */
        public Builder setSubscriptionAddress(Uri value) {
            this.mSubscriptionAddress = value;
            return this;
        }

        /**
         * Sets the capabilities. See {@link PhoneAccount#getCapabilities}.
         *
         * @param value The capabilities to set.
         * @return The builder.
         */
        public Builder setCapabilities(int value) {
            this.mCapabilities = value;
            return this;
        }

        /**
         * Sets the icon. See {@link PhoneAccount#createIconDrawable}.
         *
         * @param packageContext The package from which to load an icon.
         * @param iconResId The resource in {@code iconPackageName} representing the icon.
         * @return The builder.
         */
        public Builder setIcon(Context packageContext, int iconResId) {
            return setIcon(packageContext.getPackageName(), iconResId);
        }

        /**
         * Sets the icon. See {@link PhoneAccount#createIconDrawable}.
         *
         * @param iconPackageName The package from which to load an icon.
         * @param iconResId The resource in {@code iconPackageName} representing the icon.
         * @return The builder.
         */
        public Builder setIcon(String iconPackageName, int iconResId) {
            return setIcon(iconPackageName, iconResId, NO_ICON_TINT);
        }

        /**
         * Sets the icon. See {@link PhoneAccount#createIconDrawable}.
         *
         * @param packageContext The package from which to load an icon.
         * @param iconResId The resource in {@code iconPackageName} representing the icon.
         * @param iconTint A color with which to tint this icon.
         * @return The builder.
         */
        public Builder setIcon(Context packageContext, int iconResId, int iconTint) {
            return setIcon(packageContext.getPackageName(), iconResId, iconTint);
        }

        /**
         * Sets the icon. See {@link PhoneAccount#createIconDrawable}.
         *
         * @param iconPackageName The package from which to load an icon.
         * @param iconResId The resource in {@code iconPackageName} representing the icon.
         * @param iconTint A color with which to tint this icon.
         * @return The builder.
         */
        public Builder setIcon(String iconPackageName, int iconResId, int iconTint) {
            this.mIconPackageName = iconPackageName;
            this.mIconResId = iconResId;
            this.mIconTint = iconTint;
            return this;
        }

        /**
         * Sets the icon. See {@link PhoneAccount#createIconDrawable}.
         *
         * @param iconBitmap The icon bitmap.
         * @return The builder.
         */
        public Builder setIcon(Bitmap iconBitmap) {
            this.mIconBitmap = iconBitmap;
            this.mIconPackageName = null;
            this.mIconResId = NO_RESOURCE_ID;
            this.mIconTint = NO_ICON_TINT;
            return this;
        }

        /**
         * Sets the highlight color. See {@link PhoneAccount#getHighlightColor}.
         *
         * @param value The highlight color.
         * @return The builder.
         */
        public Builder setHighlightColor(int value) {
            this.mHighlightColor = value;
            return this;
        }

        /**
         * Sets the short description. See {@link PhoneAccount#getShortDescription}.
         *
         * @param value The short description.
         * @return The builder.
         */
        public Builder setShortDescription(CharSequence value) {
            this.mShortDescription = value;
            return this;
        }

        /**
         * Specifies an additional URI scheme supported by the {@link PhoneAccount}.
         *
         * @param uriScheme The URI scheme.
         * @return The builder.
         * @hide
         */
        public Builder addSupportedUriScheme(String uriScheme) {
            if (!TextUtils.isEmpty(uriScheme) && !mSupportedUriSchemes.contains(uriScheme)) {
                this.mSupportedUriSchemes.add(uriScheme);
            }
            return this;
        }

        /**
         * Specifies the URI schemes supported by the {@link PhoneAccount}.
         *
         * @param uriSchemes The URI schemes.
         * @return The builder.
         */
        public Builder setSupportedUriSchemes(List<String> uriSchemes) {
            mSupportedUriSchemes.clear();

            if (uriSchemes != null && !uriSchemes.isEmpty()) {
                for (String uriScheme : uriSchemes) {
                    addSupportedUriScheme(uriScheme);
                }
            }
            return this;
        }

        /**
         * Creates an instance of a {@link PhoneAccount} based on the current builder settings.
         *
         * @return The {@link PhoneAccount}.
         */
        public PhoneAccount build() {
            // If no supported URI schemes were defined, assume "tel" is supported.
            if (mSupportedUriSchemes.isEmpty()) {
                addSupportedUriScheme(SCHEME_TEL);
            }

            return new PhoneAccount(
                    mAccountHandle,
                    mAddress,
                    mSubscriptionAddress,
                    mCapabilities,
                    mIconResId,
                    mIconPackageName,
                    mIconBitmap,
                    mIconTint,
                    mHighlightColor,
                    mLabel,
                    mShortDescription,
                    mSupportedUriSchemes);
        }
    }

    private PhoneAccount(
            PhoneAccountHandle account,
            Uri address,
            Uri subscriptionAddress,
            int capabilities,
            int iconResId,
            String iconPackageName,
            Bitmap iconBitmap,
            int iconTint,
            int highlightColor,
            CharSequence label,
            CharSequence shortDescription,
            List<String> supportedUriSchemes) {
        mAccountHandle = account;
        mAddress = address;
        mSubscriptionAddress = subscriptionAddress;
        mCapabilities = capabilities;
        mIconResId = iconResId;
        mIconPackageName = iconPackageName;
        mIconBitmap = iconBitmap;
        mIconTint = iconTint;
        mHighlightColor = highlightColor;
        mLabel = label;
        mShortDescription = shortDescription;
        mSupportedUriSchemes = Collections.unmodifiableList(supportedUriSchemes);
    }

    public static Builder builder(
            PhoneAccountHandle accountHandle,
            CharSequence label) {
        return new Builder(accountHandle, label);
    }

    /**
     * Returns a builder initialized with the current {@link PhoneAccount} instance.
     *
     * @return The builder.
     * @hide
     */
    public Builder toBuilder() { return new Builder(this); }

    /**
     * The unique identifier of this {@code PhoneAccount}.
     *
     * @return A {@code PhoneAccountHandle}.
     */
    public PhoneAccountHandle getAccountHandle() {
        return mAccountHandle;
    }

    /**
     * The address (e.g., a phone number) associated with this {@code PhoneAccount}. This
     * represents the destination from which outgoing calls using this {@code PhoneAccount}
     * will appear to come, if applicable, and the destination to which incoming calls using this
     * {@code PhoneAccount} may be addressed.
     *
     * @return A address expressed as a {@code Uri}, for example, a phone number.
     */
    public Uri getAddress() {
        return mAddress;
    }

    /**
     * The raw callback number used for this {@code PhoneAccount}, as distinct from
     * {@link #getAddress()}. For the majority of {@code PhoneAccount}s this should be registered
     * as {@code null}.  It is used by the system for SIM-based {@code PhoneAccount} registration
     * where {@link android.telephony.TelephonyManager#setLine1NumberForDisplay(String, String)}
     * has been used to alter the callback number.
     * <p>
     *
     * @return The subscription number, suitable for display to the user.
     */
    public Uri getSubscriptionAddress() {
        return mSubscriptionAddress;
    }

    /**
     * The capabilities of this {@code PhoneAccount}.
     *
     * @return A bit field of flags describing this {@code PhoneAccount}'s capabilities.
     */
    public int getCapabilities() {
        return mCapabilities;
    }

    /**
     * Determines if this {@code PhoneAccount} has a capabilities specified by the passed in
     * bit mask.
     *
     * @param capability The capabilities to check.
     * @return {@code True} if the phone account has the capability.
     */
    public boolean hasCapabilities(int capability) {
        return (mCapabilities & capability) == capability;
    }

    /**
     * A short label describing a {@code PhoneAccount}.
     *
     * @return A label for this {@code PhoneAccount}.
     */
    public CharSequence getLabel() {
        return mLabel;
    }

    /**
     * A short paragraph describing this {@code PhoneAccount}.
     *
     * @return A description for this {@code PhoneAccount}.
     */
    public CharSequence getShortDescription() {
        return mShortDescription;
    }

    /**
     * The URI schemes supported by this {@code PhoneAccount}.
     *
     * @return The URI schemes.
     */
    public List<String> getSupportedUriSchemes() {
        return mSupportedUriSchemes;
    }

    /**
     * Determines if the {@link PhoneAccount} supports calls to/from addresses with a specified URI
     * scheme.
     *
     * @param uriScheme The URI scheme to check.
     * @return {@code True} if the {@code PhoneAccount} supports calls to/from addresses with the
     * specified URI scheme.
     */
    public boolean supportsUriScheme(String uriScheme) {
        if (mSupportedUriSchemes == null || uriScheme == null) {
            return false;
        }

        for (String scheme : mSupportedUriSchemes) {
            if (scheme != null && scheme.equals(uriScheme)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The icon resource ID for the icon of this {@code PhoneAccount}.
     * <p>
     * Creators of a {@code PhoneAccount} who possess the icon in static resources should prefer
     * this method of indicating the icon rather than using {@link #getIconBitmap()}, since it
     * leads to less resource usage.
     * <p>
     * Clients wishing to display a {@code PhoneAccount} should use {@link #createIconDrawable(Context)}.
     *
     * @return A resource ID.
     */
    public int getIconResId() {
        return mIconResId;
    }

    /**
     * The package name from which to load the icon of this {@code PhoneAccount}.
     * <p>
     * If this property is {@code null}, the resource {@link #getIconResId()} will be loaded from
     * the package in the {@link ComponentName} of the {@link #getAccountHandle()}.
     * <p>
     * Clients wishing to display a {@code PhoneAccount} should use {@link #createIconDrawable(Context)}.
     *
     * @return A package name.
     */
    public String getIconPackageName() {
        return mIconPackageName;
    }

    /**
     * A tint to apply to the icon of this {@code PhoneAccount}.
     *
     * @return A hexadecimal color value.
     */
    public int getIconTint() {
        return mIconTint;
    }

    /**
     * A literal icon bitmap to represent this {@code PhoneAccount} in a user interface.
     * <p>
     * If this property is specified, it is to be considered the preferred icon. Otherwise, the
     * resource specified by {@link #getIconResId()} should be used.
     * <p>
     * Clients wishing to display a {@code PhoneAccount} should use
     * {@link #createIconDrawable(Context)}.
     *
     * @return A bitmap.
     */
    public Bitmap getIconBitmap() {
        return mIconBitmap;
    }

    /**
     * A highlight color to use in displaying information about this {@code PhoneAccount}.
     *
     * @return A hexadecimal color value.
     */
    public int getHighlightColor() {
        return mHighlightColor;
    }

    /**
     * Builds and returns an icon {@code Drawable} to represent this {@code PhoneAccount} in a user
     * interface. Uses the properties {@link #getIconResId()}, {@link #getIconPackageName()}, and
     * {@link #getIconBitmap()} as necessary.
     *
     * @param context A {@code Context} to use for loading {@code Drawable}s.
     *
     * @return An icon for this {@code PhoneAccount}.
     */
    public Drawable createIconDrawable(Context context) {
        if (mIconBitmap != null) {
            return new BitmapDrawable(context.getResources(), mIconBitmap);
        }

        if (mIconResId != 0) {
            try {
                Context packageContext = context.createPackageContext(mIconPackageName, 0);
                try {
                    Drawable iconDrawable = packageContext.getDrawable(mIconResId);
                    if (mIconTint != NO_ICON_TINT) {
                        iconDrawable.setTint(mIconTint);
                    }
                    return iconDrawable;
                } catch (NotFoundException | MissingResourceException e) {
                    Log.e(this, e, "Cannot find icon %d in package %s",
                            mIconResId, mIconPackageName);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(this, "Cannot find package %s", mIconPackageName);
            }
        }

        return new ColorDrawable(Color.TRANSPARENT);
    }

    //
    // Parcelable implementation
    //

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        if (mAccountHandle == null) {
            out.writeInt(0);
        } else {
            out.writeInt(1);
            mAccountHandle.writeToParcel(out, flags);
        }
        if (mAddress == null) {
            out.writeInt(0);
        } else {
            out.writeInt(1);
            mAddress.writeToParcel(out, flags);
        }
        if (mSubscriptionAddress == null) {
            out.writeInt(0);
        } else {
            out.writeInt(1);
            mSubscriptionAddress.writeToParcel(out, flags);
        }
        out.writeInt(mCapabilities);
        out.writeInt(mIconResId);
        out.writeString(mIconPackageName);
        if (mIconBitmap == null) {
            out.writeInt(0);
        } else {
            out.writeInt(1);
            mIconBitmap.writeToParcel(out, flags);
        }
        out.writeInt(mIconTint);
        out.writeInt(mHighlightColor);
        out.writeCharSequence(mLabel);
        out.writeCharSequence(mShortDescription);
        out.writeStringList(mSupportedUriSchemes);
    }

    public static final Creator<PhoneAccount> CREATOR
            = new Creator<PhoneAccount>() {
        @Override
        public PhoneAccount createFromParcel(Parcel in) {
            return new PhoneAccount(in);
        }

        @Override
        public PhoneAccount[] newArray(int size) {
            return new PhoneAccount[size];
        }
    };

    private PhoneAccount(Parcel in) {
        if (in.readInt() > 0) {
            mAccountHandle = PhoneAccountHandle.CREATOR.createFromParcel(in);
        } else {
            mAccountHandle = null;
        }
        if (in.readInt() > 0) {
            mAddress = Uri.CREATOR.createFromParcel(in);
        } else {
            mAddress = null;
        }
        if (in.readInt() > 0) {
            mSubscriptionAddress = Uri.CREATOR.createFromParcel(in);
        } else {
            mSubscriptionAddress = null;
        }
        mCapabilities = in.readInt();
        mIconResId = in.readInt();
        mIconPackageName = in.readString();
        if (in.readInt() > 0) {
            mIconBitmap = Bitmap.CREATOR.createFromParcel(in);
        } else {
            mIconBitmap = null;
        }
        mIconTint = in.readInt();
        mHighlightColor = in.readInt();
        mLabel = in.readCharSequence();
        mShortDescription = in.readCharSequence();
        mSupportedUriSchemes = Collections.unmodifiableList(in.createStringArrayList());
    }

    /* Google code:
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append("[PhoneAccount: ")
                .append(mAccountHandle)
                .append(" Capabilities: ")
                .append(mCapabilities)
                .append(" Schemes: ");
        for (String scheme : mSupportedUriSchemes) {
            sb.append(scheme)
                    .append(" ");
        }
        sb.append("]");
        return sb.toString();
    }
    */

    // ---------------------------MTK -----------------------------------------------//

    /**
     * add for log
     * @hide
     */
    public String toString(){
        return new StringBuilder()
                .append("[(AccountHandle: ")
                .append(mAccountHandle)
                .append("), Address: ")
                /* HQ_guomiao 2015-10-22 modified for HQ01419645 begin */
                .append(Build.TYPE.equals("eng") ? mAddress : "")
                .append(", SubscriptionAddress: ")
                .append(Build.TYPE.equals("eng") ? mSubscriptionAddress : "")
                /* HQ_guomiao 2015-10-22 modified for HQ01419645 end */
                .append(", Capabilities: ")
                .append(mCapabilities)
                .append(", IconResId: ")
                .append(mIconResId)
                .append(", highlightColor: ")
                .append(mHighlightColor)
                .append(", Label: ")
                .append(mLabel)
                .append(", ShortDescription: ")
                .append(mShortDescription)
                .append(", SupportedUriSchemes: ")
                .append(mSupportedUriSchemes)
                .append("]")
                .toString();
    }

    // FIXME: There has not check the Bitmap. And think that highlightColor and
    // Bitmap change is associated.
    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof PhoneAccount)) {
            return false;
        }

        PhoneAccount targetAccount = (PhoneAccount) other;
        return Objects.equals(targetAccount.getCapabilities(), getCapabilities())
                && Objects.equals(targetAccount.getIconResId(), getIconResId())
                && Objects.equals(targetAccount.getIconTint(), getIconTint())
                && Objects.equals(targetAccount.getHighlightColor(), getHighlightColor())
                && Objects.equals(targetAccount.getIconPackageName(), getIconPackageName())
                && Objects.equals(targetAccount.getLabel(), getLabel())
                && Objects.equals(targetAccount.getShortDescription(), getShortDescription())
                && Objects.equals(targetAccount.getAddress(), getAddress())
                && Objects.equals(targetAccount.getSubscriptionAddress(), getSubscriptionAddress())
                && Objects.equals(targetAccount.getSupportedUriSchemes(), getSupportedUriSchemes())
                && Objects.equals(targetAccount.getAccountHandle(), getAccountHandle());
    }
}
