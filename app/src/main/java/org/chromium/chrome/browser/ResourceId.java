package org.chromium.chrome.browser;
import org.chromium.chrome.R;
public class ResourceId {
    public static int mapToDrawableId(int enumeratedId) {
        int[] resourceList = {
0,
R.drawable.infobar_3d_blocked,
R.drawable.infobar_autofill_cc,
R.drawable.infobar_camera,
R.drawable.infobar_microphone,
R.drawable.infobar_savepassword,
R.drawable.infobar_translate,
R.drawable.infobar_blocked_popups,
R.drawable.infobar_restore,
R.drawable.infobar_geolocation,
R.drawable.infobar_screen_share,
R.drawable.infobar_midi,
R.drawable.infobar_multiple_downloads,
R.drawable.infobar_desktop_notifications,
R.drawable.infobar_chrome,
R.drawable.infobar_protected_media_identifier,
R.drawable.infobar_subresource_filtering,
R.drawable.infobar_warning,
R.drawable.pageinfo_good,
R.drawable.pageinfo_warning,
R.drawable.pageinfo_bad,
R.drawable.pageinfo_warning,
R.drawable.pageinfo_warning,
R.drawable.pageinfo_warning,
R.drawable.amex_card,
R.drawable.ic_credit_card_black,
R.drawable.discover_card,
R.drawable.ic_credit_card_black,
R.drawable.ic_credit_card_black,
R.drawable.mc_card,
R.drawable.mir_card,
R.drawable.visa_card,
R.drawable.ic_photo_camera_black,
R.drawable.ic_info_outline_grey,
R.drawable.ic_warning_red,
R.drawable.ic_settings_black,
R.drawable.cvc_icon,
R.drawable.cvc_icon_amex,
R.drawable.pr_amex,
R.drawable.pr_dinersclub,
R.drawable.pr_discover,
R.drawable.pr_generic,
R.drawable.pr_jcb,
R.drawable.pr_mc,
R.drawable.pr_mir,
R.drawable.pr_unionpay,
R.drawable.pr_visa,
        };
        if (enumeratedId >= 0 && enumeratedId < resourceList.length) {
            return resourceList[enumeratedId];
        }
        assert false : "enumeratedId '" + enumeratedId + "' was out of range.";
        return R.drawable.missing;
    }
}
