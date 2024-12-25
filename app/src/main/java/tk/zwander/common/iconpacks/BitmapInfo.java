package tk.zwander.common.iconpacks;

import androidx.annotation.IntDef;

public class BitmapInfo {
    public static final int FLAG_THEMED = 1 << 0;
    public static final int FLAG_NO_BADGE = 1 << 1;
    public static final int FLAG_SKIP_USER_BADGE = 1 << 2;
    @IntDef(flag = true, value = {
            FLAG_THEMED,
            FLAG_NO_BADGE,
            FLAG_SKIP_USER_BADGE,
    })
    public @interface DrawableCreationFlags {}
}