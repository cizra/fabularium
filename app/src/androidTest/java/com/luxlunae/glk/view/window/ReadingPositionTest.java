package com.luxlunae.glk.view.window;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import android.text.DynamicLayout;
import android.view.ContextThemeWrapper;
import com.luxlunae.fabularium.R;
import com.luxlunae.glk.GLKConstants;
import com.luxlunae.glk.model.GLKModel;
import com.luxlunae.glk.model.stream.window.GLKTextBufferM;
import com.luxlunae.glk.model.style.GLKStyleSpan;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Reading anchors and response layout using synthetic text windows.
public class ReadingPositionTest extends InstrumentationTestCase {
    public void testReadingAnchorsAndLongResponses() throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                File checkpoint = null;
                try {
                    Context context = new ContextThemeWrapper(getInstrumentation().getTargetContext(),
                            R.style.Theme_Fabularium);
                    GLKModel host = new GLKModel((Application) context.getApplicationContext());
                    checkpoint = File.createTempFile("reading-position", ".bfms", context.getCacheDir());
                    host.mAutosaveFile = checkpoint;
                    StringBuilder text = new StringBuilder("HEADER\n>command\n");
                    for (int i = 0; i < 200; i++) text.append("History line ").append(i).append(" with words to wrap\n");
                    text.append('>');
                    GLKTextBufferV original = create(context, host, text.toString(), 600, 320);
                    SharedPreferences prefs = context.getSharedPreferences(
                            "scrollback-" + checkpoint.getName(), Context.MODE_PRIVATE);
                    original.scrollTo(0, 1234);
                    Method save = GLKTextBufferV.class.getDeclaredMethod("saveReadingPosition", boolean.class);
                    save.setAccessible(true);
                    save.invoke(original, false);
                    assertFalse(prefs.getBoolean("bottom", true));
                    assertEquals(1234, create(context, host, text.toString(), 600, 320).getScrollY());

                    int distance = prefs.getInt("distance", 0), offset = prefs.getInt("offset", 0);
                    GLKTextBufferV narrow = create(context, host, text.toString(), 300, 320);
                    DynamicLayout layout = layout(narrow);
                    int line = layout.getLineForOffset(text.length() - distance);
                    assertEquals(layout.getLineTop(line) + Math.min(offset,
                            layout.getLineBottom(line) - layout.getLineTop(line) - 1), narrow.getScrollY());

                    original.scrollTo(0, original.getChildHeight());
                    save.invoke(original, false);
                    GLKTextBufferV small = create(context, host, text.toString(), 600, 160);
                    assertEquals(small.getChildHeight() - small.mHeightLessPadding, small.getScrollY());
                    prefs.edit().putString("snapshot", "unrelated checkpoint").commit();
                    assertEquals(0, create(context, host, text.toString(), 600, 320).getScrollY());
                    prefs.edit().clear().commit();

                    host.mAutosaveFile = null; // Ordinary display policy is independent of autosave.
                    GLKTextBufferV response = create(context, host, "HEADER\n>", 600, 320);
                    response.beginResponse();
                    GLKTextBufferM buffer = (GLKTextBufferM) response.getModel();
                    StringBuilder output = new StringBuilder("\n");
                    for (int i = 0; i < 100; i++) output.append("Response line ").append(i).append('\n');
                    output.append('>');
                    buffer.getBuffer().append(output);
                    response.updateContents();
                    response.updateInputState();
                    assertEquals(layout(response).getLineTop(1), response.getScrollY());
                    response.scrollTo(0, 1000);
                    buffer.getBuffer().append(output);
                    response.updateContents();
                    response.updateInputState();
                    assertEquals("Unsolicited output must not reposition the reader", 1000, response.getScrollY());

                    GLKTextBufferV shortResponse = create(context, host, "HEADER\n>", 600, 320);
                    shortResponse.beginResponse();
                    GLKTextBufferM shortBuffer = (GLKTextBufferM) shortResponse.getModel();
                    shortBuffer.getBuffer().append("\nShort answer\n>");
                    shortResponse.updateContents();
                    shortResponse.updateInputState();
                    assertEquals(shortResponse.mHeightLessPadding,
                            shortResponse.getChildHeight() - shortResponse.getScrollY());
                } catch (Exception error) {
                    throw new AssertionError(error);
                } finally {
                    if (checkpoint != null) checkpoint.delete();
                }
            }
        });
    }

    private static GLKTextBufferV create(Context context, GLKModel host, String text, int width, int height) {
        GLKStyleSpan[] styles = new GLKStyleSpan[GLKConstants.style_NUMSTYLES];
        GLKStyleSpan.getDefaultStyles(styles, false, 16, context);
        GLKTextBufferM.GLKTextBufferBuilder builder = new GLKTextBufferM.GLKTextBufferBuilder();
        builder.setModel(host);
        builder.setDefaultStyles(styles);
        builder.setLeadingMultiplier(1.2f);
        GLKTextBufferM buffer = builder.build();
        buffer.setRock(1);
        buffer.resize(width, height);
        buffer.requestCharEvent();
        GLKTextBufferV view = new GLKTextBufferV(context, buffer, new GLKScreen(context),
                null, null, 0, 0, false);
        view.layout(0, 0, width, height);
        buffer.getBuffer().append(text);
        view.updateContents();
        view.updateInputState();
        return view;
    }

    private static DynamicLayout layout(GLKTextBufferV view) throws Exception {
        Field field = GLKTextBufferV.class.getDeclaredField("mLayout");
        field.setAccessible(true);
        return (DynamicLayout) field.get(view);
    }
}
