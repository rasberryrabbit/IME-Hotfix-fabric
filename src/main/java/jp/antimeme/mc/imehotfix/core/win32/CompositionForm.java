package jp.antimeme.mc.imehotfix.core.win32;

import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef;
import java.util.Arrays;
import java.util.List;

public class CompositionForm extends Structure {
    public int dwStyle;
    public WinDef.POINT ptCurrentPos = new WinDef.POINT();
    public WinDef.RECT rcArea = new WinDef.RECT();

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("dwStyle", "ptCurrentPos", "rcArea");
    }
}
