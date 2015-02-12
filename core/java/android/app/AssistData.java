/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.app;

import android.content.ComponentName;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewAssistData;
import android.view.ViewGroup;
import android.view.ViewRootImpl;
import android.view.WindowManagerGlobal;
import android.widget.Checkable;

import java.util.ArrayList;

/**
 * Assist data automatically created by the platform's implementation
 * of {@link Activity#onProvideAssistData}.  Retrieve it from the assist
 * data with {@link #getAssistData(android.os.Bundle)}.
 */
final public class AssistData implements Parcelable {
    static final String TAG = "AssistData";

    /**
     * Key name this data structure is stored in the Bundle generated by
     * {@link Activity#onProvideAssistData}.
     */
    public static final String ASSIST_KEY = "android:assist";

    final ComponentName mActivityComponent;

    final ArrayList<ViewNodeImpl> mRootViews = new ArrayList<>();

    ViewAssistDataImpl mTmpViewAssistDataImpl = new ViewAssistDataImpl();
    Bundle mTmpExtras = new Bundle();

    final static class ViewAssistDataImpl extends ViewAssistData {
        CharSequence mText;
        int mTextSelectionStart = -1;
        int mTextSelectionEnd = -1;
        CharSequence mHint;

        @Override
        public void setText(CharSequence text) {
            mText = text;
            mTextSelectionStart = mTextSelectionEnd = -1;
        }

        @Override
        public void setText(CharSequence text, int selectionStart, int selectionEnd) {
            mText = text;
            mTextSelectionStart = selectionStart;
            mTextSelectionEnd = selectionEnd;
        }

        @Override
        public void setHint(CharSequence hint) {
            mHint = hint;
        }

        @Override
        public CharSequence getText() {
            return mText;
        }

        @Override
        public int getTextSelectionStart() {
            return mTextSelectionStart;
        }

        @Override
        public int getTextSelectionEnd() {
            return mTextSelectionEnd;
        }

        @Override
        public CharSequence getHint() {
            return mHint;
        }
    }

    final static class ViewNodeTextImpl {
        final String mText;
        final int mTextSelectionStart;
        final int mTextSelectionEnd;
        final String mHint;

        ViewNodeTextImpl(ViewAssistDataImpl data) {
            mText = data.mText != null ? data.mText.toString() : null;
            mTextSelectionStart = data.mTextSelectionStart;
            mTextSelectionEnd = data.mTextSelectionEnd;
            mHint = data.mHint != null ? data.mHint.toString() : null;
        }

        ViewNodeTextImpl(Parcel in) {
            mText = in.readString();
            mTextSelectionStart = in.readInt();
            mTextSelectionEnd = in.readInt();
            mHint = in.readString();
        }

        void writeToParcel(Parcel out) {
            out.writeString(mText);
            out.writeInt(mTextSelectionStart);
            out.writeInt(mTextSelectionEnd);
            out.writeString(mHint);
        }
    }

    final static class ViewNodeImpl {
        final int mX;
        final int mY;
        final int mScrollX;
        final int mScrollY;
        final int mWidth;
        final int mHeight;

        static final int FLAGS_DISABLED = 0x00000001;
        static final int FLAGS_VISIBILITY_MASK = View.VISIBLE|View.INVISIBLE|View.GONE;
        static final int FLAGS_FOCUSABLE = 0x00000010;
        static final int FLAGS_FOCUSED = 0x00000020;
        static final int FLAGS_ACCESSIBILITY_FOCUSED = 0x04000000;
        static final int FLAGS_SELECTED = 0x00000040;
        static final int FLAGS_ACTIVATED = 0x40000000;
        static final int FLAGS_CHECKABLE = 0x00000100;
        static final int FLAGS_CHECKED = 0x00000200;
        static final int FLAGS_CLICKABLE = 0x00004000;
        static final int FLAGS_LONG_CLICKABLE = 0x00200000;

        final int mFlags;

        final String mClassName;
        final String mContentDescription;

        final ViewNodeTextImpl mText;
        final Bundle mExtras;

        final ViewNodeImpl[] mChildren;

        ViewNodeImpl(AssistData assistData, View view, int left, int top,
                CharSequence contentDescription) {
            mX = left;
            mY = top;
            mScrollX = view.getScrollX();
            mScrollY = view.getScrollY();
            mWidth = view.getWidth();
            mHeight = view.getHeight();
            int flags = view.getVisibility();
            if (!view.isEnabled()) {
                flags |= FLAGS_DISABLED;
            }
            if (!view.isClickable()) {
                flags |= FLAGS_CLICKABLE;
            }
            if (!view.isFocusable()) {
                flags |= FLAGS_FOCUSABLE;
            }
            if (!view.isFocused()) {
                flags |= FLAGS_FOCUSED;
            }
            if (!view.isAccessibilityFocused()) {
                flags |= FLAGS_ACCESSIBILITY_FOCUSED;
            }
            if (!view.isSelected()) {
                flags |= FLAGS_SELECTED;
            }
            if (!view.isActivated()) {
                flags |= FLAGS_ACTIVATED;
            }
            if (!view.isLongClickable()) {
                flags |= FLAGS_LONG_CLICKABLE;
            }
            if (view instanceof Checkable) {
                flags |= FLAGS_CHECKABLE;
                if (((Checkable)view).isChecked()) {
                    flags |= FLAGS_CHECKED;
                }
            }
            mFlags = flags;
            mClassName = view.getAccessibilityClassName().toString();
            mContentDescription = contentDescription != null ? contentDescription.toString() : null;
            final ViewAssistDataImpl viewData = assistData.mTmpViewAssistDataImpl;
            final Bundle extras = assistData.mTmpExtras;
            view.onProvideAssistData(viewData, extras);
            if (viewData.mText != null || viewData.mHint != null) {
                mText = new ViewNodeTextImpl(viewData);
                assistData.mTmpViewAssistDataImpl = new ViewAssistDataImpl();
            } else {
                mText = null;
            }
            if (!extras.isEmpty()) {
                mExtras = extras;
                assistData.mTmpExtras = new Bundle();
            } else {
                mExtras = null;
            }
            if (view instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup)view;
                final int NCHILDREN = vg.getChildCount();
                if (NCHILDREN > 0) {
                    mChildren = new ViewNodeImpl[NCHILDREN];
                    for (int i=0; i<NCHILDREN; i++) {
                        mChildren[i] = new ViewNodeImpl(assistData, vg.getChildAt(i));
                    }
                } else {
                    mChildren = null;
                }
            } else {
                mChildren = null;
            }
        }

        ViewNodeImpl(AssistData assistData, View view) {
            this(assistData, view, view.getLeft(), view.getTop(), view.getContentDescription());
        }

        ViewNodeImpl(Parcel in) {
            mX = in.readInt();
            mY = in.readInt();
            mScrollX = in.readInt();
            mScrollY = in.readInt();
            mWidth = in.readInt();
            mHeight = in.readInt();
            mFlags = in.readInt();
            mClassName = in.readString();
            mContentDescription = in.readString();
            if (in.readInt() != 0) {
                mText = new ViewNodeTextImpl(in);
            } else {
                mText = null;
            }
            mExtras = in.readBundle();
            final int NCHILDREN = in.readInt();
            if (NCHILDREN > 0) {
                mChildren = new ViewNodeImpl[NCHILDREN];
                for (int i=0; i<NCHILDREN; i++) {
                    mChildren[i] = new ViewNodeImpl(in);
                }
            } else {
                mChildren = null;
            }
        }

        void writeToParcel(Parcel out) {
            out.writeInt(mX);
            out.writeInt(mY);
            out.writeInt(mScrollX);
            out.writeInt(mScrollY);
            out.writeInt(mWidth);
            out.writeInt(mHeight);
            out.writeInt(mFlags);
            out.writeString(mClassName);
            out.writeString(mContentDescription);
            if (mText != null) {
                out.writeInt(1);
                mText.writeToParcel(out);
            } else {
                out.writeInt(0);
            }
            out.writeBundle(mExtras);
            if (mChildren != null) {
                final int NCHILDREN = mChildren.length;
                out.writeInt(NCHILDREN);
                for (int i=0; i<NCHILDREN; i++) {
                    mChildren[i].writeToParcel(out);
                }
            } else {
                out.writeInt(0);
            }
        }
    }

    /**
     * Provides access to information about a single view in the assist data.
     */
    static public class ViewNode {
        ViewNodeImpl mImpl;

        public ViewNode() {
        }

        public int getLeft() {
            return mImpl.mX;
        }

        public int getTop() {
            return mImpl.mY;
        }

        public int getScrollX() {
            return mImpl.mScrollX;
        }

        public int getScrollY() {
            return mImpl.mScrollY;
        }

        public int getWidth() {
            return mImpl.mWidth;
        }

        public int getHeight() {
            return mImpl.mHeight;
        }

        public int getVisibility() {
            return mImpl.mFlags&ViewNodeImpl.FLAGS_VISIBILITY_MASK;
        }

        public boolean isEnabled() {
            return (mImpl.mFlags&ViewNodeImpl.FLAGS_DISABLED) == 0;
        }

        public boolean isClickable() {
            return (mImpl.mFlags&ViewNodeImpl.FLAGS_CLICKABLE) != 0;
        }

        public boolean isFocusable() {
            return (mImpl.mFlags&ViewNodeImpl.FLAGS_FOCUSABLE) != 0;
        }

        public boolean isFocused() {
            return (mImpl.mFlags&ViewNodeImpl.FLAGS_FOCUSED) != 0;
        }

        public boolean isAccessibilityFocused() {
            return (mImpl.mFlags&ViewNodeImpl.FLAGS_ACCESSIBILITY_FOCUSED) != 0;
        }

        public boolean isCheckable() {
            return (mImpl.mFlags&ViewNodeImpl.FLAGS_CHECKABLE) != 0;
        }

        public boolean isChecked() {
            return (mImpl.mFlags&ViewNodeImpl.FLAGS_CHECKED) != 0;
        }

        public boolean isSelected() {
            return (mImpl.mFlags&ViewNodeImpl.FLAGS_SELECTED) != 0;
        }

        public boolean isActivated() {
            return (mImpl.mFlags&ViewNodeImpl.FLAGS_ACTIVATED) != 0;
        }

        public boolean isLongClickable() {
            return (mImpl.mFlags&ViewNodeImpl.FLAGS_LONG_CLICKABLE) != 0;
        }

        public String getClassName() {
            return mImpl.mClassName;
        }

        public String getContentDescription() {
            return mImpl.mContentDescription;
        }

        public String getText() {
            return mImpl.mText != null ? mImpl.mText.mText : null;
        }

        public int getTextSelectionStart() {
            return mImpl.mText != null ? mImpl.mText.mTextSelectionStart : -1;
        }

        public int getTextSelectionEnd() {
            return mImpl.mText != null ? mImpl.mText.mTextSelectionEnd : -1;
        }

        public String getHint() {
            return mImpl.mText != null ? mImpl.mText.mHint : null;
        }

        public Bundle getExtras() {
            return mImpl.mExtras;
        }

        public int getChildCount() {
            return mImpl.mChildren != null ? mImpl.mChildren.length : 0;
        }

        public void getChildAt(int index, ViewNode outNode) {
            outNode.mImpl = mImpl.mChildren[index];
        }
    }

    AssistData(Activity activity) {
        mActivityComponent = activity.getComponentName();
        ArrayList<ViewRootImpl> views = WindowManagerGlobal.getInstance().getRootViews(
                activity.getActivityToken());
        for (int i=0; i<views.size(); i++) {
            ViewRootImpl root = views.get(i);
            View view = root.getView();
            Rect rect = new Rect();
            view.getBoundsOnScreen(rect);
            CharSequence title = root.getTitle();
            mRootViews.add(new ViewNodeImpl(this, view, rect.left, rect.top,
                    title != null ? title : view.getContentDescription()));
        }
    }

    AssistData(Parcel in) {
        mActivityComponent = ComponentName.readFromParcel(in);
        final int N = in.readInt();
        for (int i=0; i<N; i++) {
            mRootViews.add(new ViewNodeImpl(in));
        }
        //dump();
    }

    /** @hide */
    public void dump() {
        Log.i(TAG, "Activity: " + mActivityComponent.flattenToShortString());
        ViewNode node = new ViewNode();
        final int N = getWindowCount();
        for (int i=0; i<N; i++) {
            Log.i(TAG, "Window #" + i + ":");
            getWindowAt(i, node);
            dump("  ", node);
        }
    }

    void dump(String prefix, ViewNode node) {
        Log.i(TAG, prefix + "View [" + node.getLeft() + "," + node.getTop()
                + " " + node.getWidth() + "x" + node.getHeight() + "]" + " " + node.getClassName());
        int scrollX = node.getScrollX();
        int scrollY = node.getScrollY();
        if (scrollX != 0 || scrollY != 0) {
            Log.i(TAG, prefix + "  Scroll: " + scrollX + "," + scrollY);
        }
        String contentDescription = node.getContentDescription();
        if (contentDescription != null) {
            Log.i(TAG, prefix + "  Content description: " + contentDescription);
        }
        String text = node.getText();
        if (text != null) {
            Log.i(TAG, prefix + "  Text (sel " + node.getTextSelectionStart() + "-"
                    + node.getTextSelectionEnd() + "): " + text);
        }
        String hint = node.getHint();
        if (hint != null) {
            Log.i(TAG, prefix + "  Hint: " + hint);
        }
        Bundle extras = node.getExtras();
        if (extras != null) {
            Log.i(TAG, prefix + "  Extras: " + extras);
        }
        final int NCHILDREN = node.getChildCount();
        if (NCHILDREN > 0) {
            Log.i(TAG, prefix + "  Children:");
            String cprefix = prefix + "    ";
            ViewNode cnode = new ViewNode();
            for (int i=0; i<NCHILDREN; i++) {
                node.getChildAt(i, cnode);
                dump(cprefix, cnode);
            }
        }
    }

    /**
     * Retrieve the framework-generated AssistData that is stored within
     * the Bundle filled in by {@link Activity#onProvideAssistData}.
     */
    public static AssistData getAssistData(Bundle assistBundle) {
        return assistBundle.getParcelable(ASSIST_KEY);
    }

    public ComponentName getActivityComponent() {
        return mActivityComponent;
    }

    /**
     * Return the number of window contents that have been collected in this assist data.
     */
    public int getWindowCount() {
        return mRootViews.size();
    }

    /**
     * Return the root view for one of the windows in the assist data.
     * @param index Which window to retrieve, may be 0 to {@link #getWindowCount()}-1.
     * @param outNode Node in which to place the window's root view.
     */
    public void getWindowAt(int index, ViewNode outNode) {
        outNode.mImpl = mRootViews.get(index);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        int start = out.dataPosition();
        ComponentName.writeToParcel(mActivityComponent, out);
        final int N = mRootViews.size();
        out.writeInt(N);
        for (int i=0; i<N; i++) {
            mRootViews.get(i).writeToParcel(out);
        }
        Log.i(TAG, "Flattened assist data: " + (out.dataPosition() - start) + " bytes");
    }

    public static final Parcelable.Creator<AssistData> CREATOR
            = new Parcelable.Creator<AssistData>() {
        public AssistData createFromParcel(Parcel in) {
            return new AssistData(in);
        }

        public AssistData[] newArray(int size) {
            return new AssistData[size];
        }
    };
}
