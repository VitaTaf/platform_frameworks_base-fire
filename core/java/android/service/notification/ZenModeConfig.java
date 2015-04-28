/**
 * Copyright (c) 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.notification;

import android.app.NotificationManager.Policy;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted configuration for zen mode.
 *
 * @hide
 */
public class ZenModeConfig implements Parcelable {
    private static String TAG = "ZenModeConfig";

    public static final int SOURCE_ANYONE = 0;
    public static final int SOURCE_CONTACT = 1;
    public static final int SOURCE_STAR = 2;
    public static final int MAX_SOURCE = SOURCE_STAR;

    public static final int[] ALL_DAYS = { Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
            Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY };
    public static final int[] WEEKNIGHT_DAYS = { Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
            Calendar.WEDNESDAY, Calendar.THURSDAY };
    public static final int[] WEEKEND_DAYS = { Calendar.FRIDAY, Calendar.SATURDAY };

    public static final int[] MINUTE_BUCKETS = generateMinuteBuckets();
    private static final int SECONDS_MS = 1000;
    private static final int MINUTES_MS = 60 * SECONDS_MS;
    private static final int ZERO_VALUE_MS = 10 * SECONDS_MS;

    private static final boolean DEFAULT_ALLOW_REMINDERS = true;
    private static final boolean DEFAULT_ALLOW_EVENTS = true;

    private static final int XML_VERSION = 2;
    private static final String ZEN_TAG = "zen";
    private static final String ZEN_ATT_VERSION = "version";
    private static final String ALLOW_TAG = "allow";
    private static final String ALLOW_ATT_CALLS = "calls";
    private static final String ALLOW_ATT_MESSAGES = "messages";
    private static final String ALLOW_ATT_FROM = "from";
    private static final String ALLOW_ATT_REMINDERS = "reminders";
    private static final String ALLOW_ATT_EVENTS = "events";

    private static final String CONDITION_TAG = "condition";
    private static final String CONDITION_ATT_COMPONENT = "component";
    private static final String CONDITION_ATT_ID = "id";
    private static final String CONDITION_ATT_SUMMARY = "summary";
    private static final String CONDITION_ATT_LINE1 = "line1";
    private static final String CONDITION_ATT_LINE2 = "line2";
    private static final String CONDITION_ATT_ICON = "icon";
    private static final String CONDITION_ATT_STATE = "state";
    private static final String CONDITION_ATT_FLAGS = "flags";

    private static final String MANUAL_TAG = "manual";
    private static final String AUTOMATIC_TAG = "automatic";

    private static final String RULE_ATT_ID = "id";
    private static final String RULE_ATT_ENABLED = "enabled";
    private static final String RULE_ATT_SNOOZING = "snoozing";
    private static final String RULE_ATT_NAME = "name";
    private static final String RULE_ATT_COMPONENT = "component";
    private static final String RULE_ATT_ZEN = "zen";
    private static final String RULE_ATT_CONDITION_ID = "conditionId";

    public boolean allowCalls;
    public boolean allowMessages;
    public boolean allowReminders = DEFAULT_ALLOW_REMINDERS;
    public boolean allowEvents = DEFAULT_ALLOW_EVENTS;
    public int allowFrom = SOURCE_ANYONE;

    public ZenRule manualRule;
    public ArrayMap<String, ZenRule> automaticRules = new ArrayMap<>();

    public ZenModeConfig() { }

    public ZenModeConfig(Parcel source) {
        allowCalls = source.readInt() == 1;
        allowMessages = source.readInt() == 1;
        allowReminders = source.readInt() == 1;
        allowEvents = source.readInt() == 1;
        allowFrom = source.readInt();
        manualRule = source.readParcelable(null);
        final int len = source.readInt();
        if (len > 0) {
            final String[] ids = new String[len];
            final ZenRule[] rules = new ZenRule[len];
            source.readStringArray(ids);
            source.readTypedArray(rules, ZenRule.CREATOR);
            for (int i = 0; i < len; i++) {
                automaticRules.put(ids[i], rules[i]);
            }
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(allowCalls ? 1 : 0);
        dest.writeInt(allowMessages ? 1 : 0);
        dest.writeInt(allowReminders ? 1 : 0);
        dest.writeInt(allowEvents ? 1 : 0);
        dest.writeInt(allowFrom);
        dest.writeParcelable(manualRule, 0);
        if (!automaticRules.isEmpty()) {
            final int len = automaticRules.size();
            final String[] ids = new String[len];
            final ZenRule[] rules = new ZenRule[len];
            for (int i = 0; i < len; i++) {
                ids[i] = automaticRules.keyAt(i);
                rules[i] = automaticRules.valueAt(i);
            }
            dest.writeInt(len);
            dest.writeStringArray(ids);
            dest.writeTypedArray(rules, 0);
        } else {
            dest.writeInt(0);
        }
    }

    @Override
    public String toString() {
        return new StringBuilder(ZenModeConfig.class.getSimpleName()).append('[')
            .append("allowCalls=").append(allowCalls)
            .append(",allowMessages=").append(allowMessages)
            .append(",allowFrom=").append(sourceToString(allowFrom))
            .append(",allowReminders=").append(allowReminders)
            .append(",allowEvents=").append(allowEvents)
            .append(",automaticRules=").append(automaticRules)
            .append(",manualRule=").append(manualRule)
            .append(']').toString();
    }

    public boolean isValid() {
        if (!isValidManualRule(manualRule)) return false;
        final int N = automaticRules.size();
        for (int i = 0; i < N; i++) {
            if (!isValidAutomaticRule(automaticRules.valueAt(i))) return false;
        }
        return true;
    }

    private static boolean isValidManualRule(ZenRule rule) {
        return rule == null || Global.isValidZenMode(rule.zenMode) && sameCondition(rule);
    }

    private static boolean isValidAutomaticRule(ZenRule rule) {
        return rule != null && !TextUtils.isEmpty(rule.name) && Global.isValidZenMode(rule.zenMode)
                && rule.conditionId != null && sameCondition(rule);
    }

    private static boolean sameCondition(ZenRule rule) {
        if (rule == null) return false;
        if (rule.conditionId == null) {
            return rule.condition == null;
        } else {
            return rule.condition == null || rule.conditionId.equals(rule.condition.id);
        }
    }

    private static int[] generateMinuteBuckets() {
        final int maxHrs = 12;
        final int[] buckets = new int[maxHrs + 3];
        buckets[0] = 15;
        buckets[1] = 30;
        buckets[2] = 45;
        for (int i = 1; i <= maxHrs; i++) {
            buckets[2 + i] = 60 * i;
        }
        return buckets;
    }

    public static String sourceToString(int source) {
        switch (source) {
            case SOURCE_ANYONE:
                return "anyone";
            case SOURCE_CONTACT:
                return "contacts";
            case SOURCE_STAR:
                return "stars";
            default:
                return "UNKNOWN";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ZenModeConfig)) return false;
        if (o == this) return true;
        final ZenModeConfig other = (ZenModeConfig) o;
        return other.allowCalls == allowCalls
                && other.allowMessages == allowMessages
                && other.allowFrom == allowFrom
                && other.allowReminders == allowReminders
                && other.allowEvents == allowEvents
                && Objects.equals(other.automaticRules, automaticRules)
                && Objects.equals(other.manualRule, manualRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowCalls, allowMessages, allowFrom, allowReminders, allowEvents,
                automaticRules, manualRule);
    }

    private static String toDayList(int[] days) {
        if (days == null || days.length == 0) return "";
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < days.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(days[i]);
        }
        return sb.toString();
    }

    private static int[] tryParseDayList(String dayList, String sep) {
        if (dayList == null) return null;
        final String[] tokens = dayList.split(sep);
        if (tokens.length == 0) return null;
        final int[] rt = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            final int day = tryParseInt(tokens[i], -1);
            if (day == -1) return null;
            rt[i] = day;
        }
        return rt;
    }

    private static int tryParseInt(String value, int defValue) {
        if (TextUtils.isEmpty(value)) return defValue;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    public static ZenModeConfig readXml(XmlPullParser parser, Migration migration)
            throws XmlPullParserException, IOException {
        int type = parser.getEventType();
        if (type != XmlPullParser.START_TAG) return null;
        String tag = parser.getName();
        if (!ZEN_TAG.equals(tag)) return null;
        final ZenModeConfig rt = new ZenModeConfig();
        final int version = safeInt(parser, ZEN_ATT_VERSION, XML_VERSION);
        if (version == 1) {
            final XmlV1 v1 = XmlV1.readXml(parser);
            return migration.migrate(v1);
        }
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
            tag = parser.getName();
            if (type == XmlPullParser.END_TAG && ZEN_TAG.equals(tag)) {
                return rt;
            }
            if (type == XmlPullParser.START_TAG) {
                if (ALLOW_TAG.equals(tag)) {
                    rt.allowCalls = safeBoolean(parser, ALLOW_ATT_CALLS, false);
                    rt.allowMessages = safeBoolean(parser, ALLOW_ATT_MESSAGES, false);
                    rt.allowReminders = safeBoolean(parser, ALLOW_ATT_REMINDERS,
                            DEFAULT_ALLOW_REMINDERS);
                    rt.allowEvents = safeBoolean(parser, ALLOW_ATT_EVENTS, DEFAULT_ALLOW_EVENTS);
                    rt.allowFrom = safeInt(parser, ALLOW_ATT_FROM, SOURCE_ANYONE);
                    if (rt.allowFrom < SOURCE_ANYONE || rt.allowFrom > MAX_SOURCE) {
                        throw new IndexOutOfBoundsException("bad source in config:" + rt.allowFrom);
                    }
                } else if (MANUAL_TAG.equals(tag)) {
                    rt.manualRule = readRuleXml(parser, false /*conditionRequired*/);
                } else if (AUTOMATIC_TAG.equals(tag)) {
                    final String id = parser.getAttributeValue(null, RULE_ATT_ID);
                    final ZenRule automaticRule = readRuleXml(parser, true /*conditionRequired*/);
                    if (id != null && automaticRule != null) {
                        rt.automaticRules.put(id, automaticRule);
                    }
                }
            }
        }
        throw new IllegalStateException("Failed to reach END_DOCUMENT");
    }

    public void writeXml(XmlSerializer out) throws IOException {
        out.startTag(null, ZEN_TAG);
        out.attribute(null, ZEN_ATT_VERSION, Integer.toString(XML_VERSION));

        out.startTag(null, ALLOW_TAG);
        out.attribute(null, ALLOW_ATT_CALLS, Boolean.toString(allowCalls));
        out.attribute(null, ALLOW_ATT_MESSAGES, Boolean.toString(allowMessages));
        out.attribute(null, ALLOW_ATT_REMINDERS, Boolean.toString(allowReminders));
        out.attribute(null, ALLOW_ATT_EVENTS, Boolean.toString(allowEvents));
        out.attribute(null, ALLOW_ATT_FROM, Integer.toString(allowFrom));
        out.endTag(null, ALLOW_TAG);

        if (manualRule != null) {
            out.startTag(null, MANUAL_TAG);
            writeRuleXml(manualRule, out);
            out.endTag(null, MANUAL_TAG);
        }
        final int N = automaticRules.size();
        for (int i = 0; i < N; i++) {
            final String id = automaticRules.keyAt(i);
            final ZenRule automaticRule = automaticRules.valueAt(i);
            out.startTag(null, AUTOMATIC_TAG);
            out.attribute(null, RULE_ATT_ID, id);
            writeRuleXml(automaticRule, out);
            out.endTag(null, AUTOMATIC_TAG);
        }
        out.endTag(null, ZEN_TAG);
    }

    public static ZenRule readRuleXml(XmlPullParser parser, boolean conditionRequired) {
        final ZenRule rt = new ZenRule();
        rt.enabled = safeBoolean(parser, RULE_ATT_ENABLED, true);
        rt.snoozing = safeBoolean(parser, RULE_ATT_SNOOZING, false);
        rt.name = parser.getAttributeValue(null, RULE_ATT_NAME);
        final String zen = parser.getAttributeValue(null, RULE_ATT_ZEN);
        rt.zenMode = tryParseZenMode(zen, -1);
        if (rt.zenMode == -1) {
            Slog.w(TAG, "Bad zen mode in rule xml:" + zen);
            return null;
        }
        rt.conditionId = safeUri(parser, RULE_ATT_CONDITION_ID);
        rt.component = safeComponentName(parser, RULE_ATT_COMPONENT);
        rt.condition = readConditionXml(parser);
        return rt.condition != null || !conditionRequired ? rt : null;
    }

    public static void writeRuleXml(ZenRule rule, XmlSerializer out) throws IOException {
        out.attribute(null, RULE_ATT_ENABLED, Boolean.toString(rule.enabled));
        out.attribute(null, RULE_ATT_SNOOZING, Boolean.toString(rule.snoozing));
        if (rule.name != null) {
            out.attribute(null, RULE_ATT_NAME, rule.name);
        }
        out.attribute(null, RULE_ATT_ZEN, Integer.toString(rule.zenMode));
        if (rule.component != null) {
            out.attribute(null, RULE_ATT_COMPONENT, rule.component.flattenToString());
        }
        if (rule.conditionId != null) {
            out.attribute(null, RULE_ATT_CONDITION_ID, rule.conditionId.toString());
        }
        if (rule.condition != null) {
            writeConditionXml(rule.condition, out);
        }
    }

    public static Condition readConditionXml(XmlPullParser parser) {
        final Uri id = safeUri(parser, CONDITION_ATT_ID);
        if (id == null) return null;
        final String summary = parser.getAttributeValue(null, CONDITION_ATT_SUMMARY);
        final String line1 = parser.getAttributeValue(null, CONDITION_ATT_LINE1);
        final String line2 = parser.getAttributeValue(null, CONDITION_ATT_LINE2);
        final int icon = safeInt(parser, CONDITION_ATT_ICON, -1);
        final int state = safeInt(parser, CONDITION_ATT_STATE, -1);
        final int flags = safeInt(parser, CONDITION_ATT_FLAGS, -1);
        try {
            return new Condition(id, summary, line1, line2, icon, state, flags);
        } catch (IllegalArgumentException e) {
            Slog.w(TAG, "Unable to read condition xml", e);
            return null;
        }
    }

    public static void writeConditionXml(Condition c, XmlSerializer out) throws IOException {
        out.attribute(null, CONDITION_ATT_ID, c.id.toString());
        out.attribute(null, CONDITION_ATT_SUMMARY, c.summary);
        out.attribute(null, CONDITION_ATT_LINE1, c.line1);
        out.attribute(null, CONDITION_ATT_LINE2, c.line2);
        out.attribute(null, CONDITION_ATT_ICON, Integer.toString(c.icon));
        out.attribute(null, CONDITION_ATT_STATE, Integer.toString(c.state));
        out.attribute(null, CONDITION_ATT_FLAGS, Integer.toString(c.flags));
    }

    public static boolean isValidHour(int val) {
        return val >= 0 && val < 24;
    }

    public static boolean isValidMinute(int val) {
        return val >= 0 && val < 60;
    }

    private static boolean safeBoolean(XmlPullParser parser, String att, boolean defValue) {
        final String val = parser.getAttributeValue(null, att);
        if (TextUtils.isEmpty(val)) return defValue;
        return Boolean.valueOf(val);
    }

    private static int safeInt(XmlPullParser parser, String att, int defValue) {
        final String val = parser.getAttributeValue(null, att);
        return tryParseInt(val, defValue);
    }

    private static ComponentName safeComponentName(XmlPullParser parser, String att) {
        final String val = parser.getAttributeValue(null, att);
        if (TextUtils.isEmpty(val)) return null;
        return ComponentName.unflattenFromString(val);
    }

    private static Uri safeUri(XmlPullParser parser, String att) {
        final String val = parser.getAttributeValue(null, att);
        if (TextUtils.isEmpty(val)) return null;
        return Uri.parse(val);
    }

    public ArraySet<String> getAutomaticRuleNames() {
        final ArraySet<String> rt = new ArraySet<String>();
        for (int i = 0; i < automaticRules.size(); i++) {
            rt.add(automaticRules.valueAt(i).name);
        }
        return rt;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public ZenModeConfig copy() {
        final Parcel parcel = Parcel.obtain();
        try {
            writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return new ZenModeConfig(parcel);
        } finally {
            parcel.recycle();
        }
    }

    public static final Parcelable.Creator<ZenModeConfig> CREATOR
            = new Parcelable.Creator<ZenModeConfig>() {
        @Override
        public ZenModeConfig createFromParcel(Parcel source) {
            return new ZenModeConfig(source);
        }

        @Override
        public ZenModeConfig[] newArray(int size) {
            return new ZenModeConfig[size];
        }
    };

    public Policy toNotificationPolicy() {
        int priorityCategories = 0;
        int prioritySenders = Policy.PRIORITY_SENDERS_ANY;
        if (allowCalls) {
            priorityCategories |= Policy.PRIORITY_CATEGORY_CALLS;
        }
        if (allowMessages) {
            priorityCategories |= Policy.PRIORITY_CATEGORY_MESSAGES;
        }
        if (allowEvents) {
            priorityCategories |= Policy.PRIORITY_CATEGORY_EVENTS;
        }
        if (allowReminders) {
            priorityCategories |= Policy.PRIORITY_CATEGORY_REMINDERS;
        }
        if (allowRepeatCallers) {
            priorityCategories |= Policy.PRIORITY_CATEGORY_REPEAT_CALLERS;
        }
        switch (allowFrom) {
            case SOURCE_ANYONE:
                prioritySenders = Policy.PRIORITY_SENDERS_ANY;
                break;
            case SOURCE_CONTACT:
                prioritySenders = Policy.PRIORITY_SENDERS_CONTACTS;
                break;
            case SOURCE_STAR:
                prioritySenders = Policy.PRIORITY_SENDERS_STARRED;
                break;
        }
        return new Policy(priorityCategories, prioritySenders);
    }

    public void applyNotificationPolicy(Policy policy) {
        if (policy == null) return;
        allowCalls = (policy.priorityCategories & Policy.PRIORITY_CATEGORY_CALLS) != 0;
        allowMessages = (policy.priorityCategories & Policy.PRIORITY_CATEGORY_MESSAGES) != 0;
        allowEvents = (policy.priorityCategories & Policy.PRIORITY_CATEGORY_EVENTS) != 0;
        allowReminders = (policy.priorityCategories & Policy.PRIORITY_CATEGORY_REMINDERS) != 0;
        allowRepeatCallers = (policy.priorityCategories & Policy.PRIORITY_CATEGORY_REPEAT_CALLERS)
                != 0;
        switch (policy.prioritySenders) {
            case Policy.PRIORITY_SENDERS_CONTACTS:
                allowFrom = SOURCE_CONTACT;
                break;
            case Policy.PRIORITY_SENDERS_STARRED:
                allowFrom = SOURCE_STAR;
                break;
            default:
                allowFrom = SOURCE_ANYONE;
                break;
        }
    }

    public static Condition toTimeCondition(Context context, int minutesFromNow, int userHandle) {
        final long now = System.currentTimeMillis();
        final long millis = minutesFromNow == 0 ? ZERO_VALUE_MS : minutesFromNow * MINUTES_MS;
        return toTimeCondition(context, now + millis, minutesFromNow, now, userHandle);
    }

    public static Condition toTimeCondition(Context context, long time, int minutes, long now,
            int userHandle) {
        final int num, summaryResId, line1ResId;
        if (minutes < 60) {
            // display as minutes
            num = minutes;
            summaryResId = R.plurals.zen_mode_duration_minutes_summary;
            line1ResId = R.plurals.zen_mode_duration_minutes;
        } else {
            // display as hours
            num =  Math.round(minutes / 60f);
            summaryResId = com.android.internal.R.plurals.zen_mode_duration_hours_summary;
            line1ResId = com.android.internal.R.plurals.zen_mode_duration_hours;
        }
        final String skeleton = DateFormat.is24HourFormat(context, userHandle) ? "Hm" : "hma";
        final String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        final CharSequence formattedTime = DateFormat.format(pattern, time);
        final Resources res = context.getResources();
        final String summary = res.getQuantityString(summaryResId, num, num, formattedTime);
        final String line1 = res.getQuantityString(line1ResId, num, num, formattedTime);
        final String line2 = res.getString(R.string.zen_mode_until, formattedTime);
        final Uri id = toCountdownConditionId(time);
        return new Condition(id, summary, line1, line2, 0, Condition.STATE_TRUE,
                Condition.FLAG_RELEVANT_NOW);
    }

    // For built-in conditions
    public static final String SYSTEM_AUTHORITY = "android";

    // Built-in countdown conditions, e.g. condition://android/countdown/1399917958951
    public static final String COUNTDOWN_PATH = "countdown";

    public static Uri toCountdownConditionId(long time) {
        return new Uri.Builder().scheme(Condition.SCHEME)
                .authority(SYSTEM_AUTHORITY)
                .appendPath(COUNTDOWN_PATH)
                .appendPath(Long.toString(time))
                .build();
    }

    public static long tryParseCountdownConditionId(Uri conditionId) {
        if (!Condition.isValidId(conditionId, SYSTEM_AUTHORITY)) return 0;
        if (conditionId.getPathSegments().size() != 2
                || !COUNTDOWN_PATH.equals(conditionId.getPathSegments().get(0))) return 0;
        try {
            return Long.parseLong(conditionId.getPathSegments().get(1));
        } catch (RuntimeException e) {
            Slog.w(TAG, "Error parsing countdown condition: " + conditionId, e);
            return 0;
        }
    }

    public static boolean isValidCountdownConditionId(Uri conditionId) {
        return tryParseCountdownConditionId(conditionId) != 0;
    }

    // built-in schedule conditions
    public static final String SCHEDULE_PATH = "schedule";

    public static class ScheduleInfo {
        public int[] days;
        public int startHour;
        public int startMinute;
        public int endHour;
        public int endMinute;

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ScheduleInfo)) return false;
            final ScheduleInfo other = (ScheduleInfo) o;
            return toDayList(days).equals(toDayList(other.days))
                    && startHour == other.startHour
                    && startMinute == other.startMinute
                    && endHour == other.endHour
                    && endMinute == other.endMinute;
        }

        public ScheduleInfo copy() {
            final ScheduleInfo rt = new ScheduleInfo();
            if (days != null) {
                rt.days = new int[days.length];
                System.arraycopy(days, 0, rt.days, 0, days.length);
            }
            rt.startHour = startHour;
            rt.startMinute = startMinute;
            rt.endHour = endHour;
            rt.endMinute = endMinute;
            return rt;
        }
    }

    public static Uri toScheduleConditionId(ScheduleInfo schedule) {
        return new Uri.Builder().scheme(Condition.SCHEME)
                .authority(SYSTEM_AUTHORITY)
                .appendPath(SCHEDULE_PATH)
                .appendQueryParameter("days", toDayList(schedule.days))
                .appendQueryParameter("start", schedule.startHour + "." + schedule.startMinute)
                .appendQueryParameter("end", schedule.endHour + "." + schedule.endMinute)
                .build();
    }

    public static boolean isValidScheduleConditionId(Uri conditionId) {
        return tryParseScheduleConditionId(conditionId) != null;
    }

    public static ScheduleInfo tryParseScheduleConditionId(Uri conditionId) {
        final boolean isSchedule =  conditionId != null
                && conditionId.getScheme().equals(Condition.SCHEME)
                && conditionId.getAuthority().equals(ZenModeConfig.SYSTEM_AUTHORITY)
                && conditionId.getPathSegments().size() == 1
                && conditionId.getPathSegments().get(0).equals(ZenModeConfig.SCHEDULE_PATH);
        if (!isSchedule) return null;
        final int[] start = tryParseHourAndMinute(conditionId.getQueryParameter("start"));
        final int[] end = tryParseHourAndMinute(conditionId.getQueryParameter("end"));
        if (start == null || end == null) return null;
        final ScheduleInfo rt = new ScheduleInfo();
        rt.days = tryParseDayList(conditionId.getQueryParameter("days"), "\\.");
        rt.startHour = start[0];
        rt.startMinute = start[1];
        rt.endHour = end[0];
        rt.endMinute = end[1];
        return rt;
    }

    private static int[] tryParseHourAndMinute(String value) {
        if (TextUtils.isEmpty(value)) return null;
        final int i = value.indexOf('.');
        if (i < 1 || i >= value.length() - 1) return null;
        final int hour = tryParseInt(value.substring(0, i), -1);
        final int minute = tryParseInt(value.substring(i + 1), -1);
        return isValidHour(hour) && isValidMinute(minute) ? new int[] { hour, minute } : null;
    }

    private static int tryParseZenMode(String value, int defValue) {
        final int rt = tryParseInt(value, defValue);
        return Global.isValidZenMode(rt) ? rt : defValue;
    }

    public String newRuleId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String getConditionLine1(Context context, ZenModeConfig config,
            int userHandle) {
        return getConditionLine(context, config, userHandle, true /*useLine1*/);
    }

    public static String getConditionSummary(Context context, ZenModeConfig config,
            int userHandle) {
        return getConditionLine(context, config, userHandle, false /*useLine1*/);
    }

    private static String getConditionLine(Context context, ZenModeConfig config,
            int userHandle, boolean useLine1) {
        if (config == null) return "";
        if (config.manualRule != null) {
            final Uri id = config.manualRule.conditionId;
            if (id == null) {
                return context.getString(com.android.internal.R.string.zen_mode_forever);
            }
            final long time = tryParseCountdownConditionId(id);
            Condition c = config.manualRule.condition;
            if (time > 0) {
                final long now = System.currentTimeMillis();
                final long span = time - now;
                c = toTimeCondition(context,
                        time, Math.round(span / (float) MINUTES_MS), now, userHandle);
            }
            final String rt = c == null ? "" : useLine1 ? c.line1 : c.summary;
            return TextUtils.isEmpty(rt) ? "" : rt;
        }
        String summary = "";
        for (ZenRule automaticRule : config.automaticRules.values()) {
            if (automaticRule.enabled && !automaticRule.snoozing
                    && automaticRule.isTrueOrUnknown()) {
                if (summary.isEmpty()) {
                    summary = automaticRule.name;
                } else {
                    summary = context.getResources()
                            .getString(R.string.zen_mode_rule_name_combination, summary,
                                    automaticRule.name);
                }
            }
        }
        return summary;
    }

    public static class ZenRule implements Parcelable {
        public boolean enabled;
        public boolean snoozing;         // user manually disabled this instance
        public String name;              // required for automatic (unique)
        public int zenMode;
        public Uri conditionId;          // required for automatic
        public Condition condition;      // optional
        public ComponentName component;  // optional

        public ZenRule() { }

        public ZenRule(Parcel source) {
            enabled = source.readInt() == 1;
            snoozing = source.readInt() == 1;
            if (source.readInt() == 1) {
                name = source.readString();
            }
            zenMode = source.readInt();
            conditionId = source.readParcelable(null);
            condition = source.readParcelable(null);
            component = source.readParcelable(null);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(enabled ? 1 : 0);
            dest.writeInt(snoozing ? 1 : 0);
            if (name != null) {
                dest.writeInt(1);
                dest.writeString(name);
            } else {
                dest.writeInt(0);
            }
            dest.writeInt(zenMode);
            dest.writeParcelable(conditionId, 0);
            dest.writeParcelable(condition, 0);
            dest.writeParcelable(component, 0);
        }

        @Override
        public String toString() {
            return new StringBuilder(ZenRule.class.getSimpleName()).append('[')
                    .append("enabled=").append(enabled)
                    .append(",snoozing=").append(snoozing)
                    .append(",name=").append(name)
                    .append(",zenMode=").append(Global.zenModeToString(zenMode))
                    .append(",conditionId=").append(conditionId)
                    .append(",condition=").append(condition)
                    .append(",component=").append(component)
                    .append(']').toString();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ZenRule)) return false;
            if (o == this) return true;
            final ZenRule other = (ZenRule) o;
            return other.enabled == enabled
                    && other.snoozing == snoozing
                    && Objects.equals(other.name, name)
                    && other.zenMode == zenMode
                    && Objects.equals(other.conditionId, conditionId)
                    && Objects.equals(other.condition, condition)
                    && Objects.equals(other.component, component);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabled, snoozing, name, zenMode, conditionId, condition,
                    component);
        }

        public boolean isTrueOrUnknown() {
            return condition == null || condition.state == Condition.STATE_TRUE
                    || condition.state == Condition.STATE_UNKNOWN;
        }

        public static final Parcelable.Creator<ZenRule> CREATOR
                = new Parcelable.Creator<ZenRule>() {
            @Override
            public ZenRule createFromParcel(Parcel source) {
                return new ZenRule(source);
            }
            @Override
            public ZenRule[] newArray(int size) {
                return new ZenRule[size];
            }
        };
    }

    // Legacy config
    public static final class XmlV1 {
        public static final String SLEEP_MODE_NIGHTS = "nights";
        public static final String SLEEP_MODE_WEEKNIGHTS = "weeknights";
        public static final String SLEEP_MODE_DAYS_PREFIX = "days:";

        private static final String EXIT_CONDITION_TAG = "exitCondition";
        private static final String EXIT_CONDITION_ATT_COMPONENT = "component";
        private static final String SLEEP_TAG = "sleep";
        private static final String SLEEP_ATT_MODE = "mode";
        private static final String SLEEP_ATT_NONE = "none";

        private static final String SLEEP_ATT_START_HR = "startHour";
        private static final String SLEEP_ATT_START_MIN = "startMin";
        private static final String SLEEP_ATT_END_HR = "endHour";
        private static final String SLEEP_ATT_END_MIN = "endMin";

        public boolean allowCalls;
        public boolean allowMessages;
        public boolean allowReminders = DEFAULT_ALLOW_REMINDERS;
        public boolean allowEvents = DEFAULT_ALLOW_EVENTS;
        public int allowFrom = SOURCE_ANYONE;

        public String sleepMode;     // nights, weeknights, days:1,2,3  Calendar.days
        public int sleepStartHour;   // 0-23
        public int sleepStartMinute; // 0-59
        public int sleepEndHour;
        public int sleepEndMinute;
        public boolean sleepNone;    // false = priority, true = none
        public ComponentName[] conditionComponents;
        public Uri[] conditionIds;
        public Condition exitCondition;  // manual exit condition
        public ComponentName exitConditionComponent;  // manual exit condition component

        private static boolean isValidSleepMode(String sleepMode) {
            return sleepMode == null || sleepMode.equals(SLEEP_MODE_NIGHTS)
                    || sleepMode.equals(SLEEP_MODE_WEEKNIGHTS) || tryParseDays(sleepMode) != null;
        }

        public static int[] tryParseDays(String sleepMode) {
            if (sleepMode == null) return null;
            sleepMode = sleepMode.trim();
            if (SLEEP_MODE_NIGHTS.equals(sleepMode)) return ALL_DAYS;
            if (SLEEP_MODE_WEEKNIGHTS.equals(sleepMode)) return WEEKNIGHT_DAYS;
            if (!sleepMode.startsWith(SLEEP_MODE_DAYS_PREFIX)) return null;
            if (sleepMode.equals(SLEEP_MODE_DAYS_PREFIX)) return null;
            return tryParseDayList(sleepMode.substring(SLEEP_MODE_DAYS_PREFIX.length()), ",");
        }

        public static XmlV1 readXml(XmlPullParser parser)
                throws XmlPullParserException, IOException {
            int type;
            String tag;
            XmlV1 rt = new XmlV1();
            final ArrayList<ComponentName> conditionComponents = new ArrayList<ComponentName>();
            final ArrayList<Uri> conditionIds = new ArrayList<Uri>();
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                tag = parser.getName();
                if (type == XmlPullParser.END_TAG && ZEN_TAG.equals(tag)) {
                    if (!conditionComponents.isEmpty()) {
                        rt.conditionComponents = conditionComponents
                                .toArray(new ComponentName[conditionComponents.size()]);
                        rt.conditionIds = conditionIds.toArray(new Uri[conditionIds.size()]);
                    }
                    return rt;
                }
                if (type == XmlPullParser.START_TAG) {
                    if (ALLOW_TAG.equals(tag)) {
                        rt.allowCalls = safeBoolean(parser, ALLOW_ATT_CALLS, false);
                        rt.allowMessages = safeBoolean(parser, ALLOW_ATT_MESSAGES, false);
                        rt.allowReminders = safeBoolean(parser, ALLOW_ATT_REMINDERS,
                                DEFAULT_ALLOW_REMINDERS);
                        rt.allowEvents = safeBoolean(parser, ALLOW_ATT_EVENTS,
                                DEFAULT_ALLOW_EVENTS);
                        rt.allowFrom = safeInt(parser, ALLOW_ATT_FROM, SOURCE_ANYONE);
                        if (rt.allowFrom < SOURCE_ANYONE || rt.allowFrom > MAX_SOURCE) {
                            throw new IndexOutOfBoundsException("bad source in config:"
                                    + rt.allowFrom);
                        }
                    } else if (SLEEP_TAG.equals(tag)) {
                        final String mode = parser.getAttributeValue(null, SLEEP_ATT_MODE);
                        rt.sleepMode = isValidSleepMode(mode)? mode : null;
                        rt.sleepNone = safeBoolean(parser, SLEEP_ATT_NONE, false);
                        final int startHour = safeInt(parser, SLEEP_ATT_START_HR, 0);
                        final int startMinute = safeInt(parser, SLEEP_ATT_START_MIN, 0);
                        final int endHour = safeInt(parser, SLEEP_ATT_END_HR, 0);
                        final int endMinute = safeInt(parser, SLEEP_ATT_END_MIN, 0);
                        rt.sleepStartHour = isValidHour(startHour) ? startHour : 0;
                        rt.sleepStartMinute = isValidMinute(startMinute) ? startMinute : 0;
                        rt.sleepEndHour = isValidHour(endHour) ? endHour : 0;
                        rt.sleepEndMinute = isValidMinute(endMinute) ? endMinute : 0;
                    } else if (CONDITION_TAG.equals(tag)) {
                        final ComponentName component =
                                safeComponentName(parser, CONDITION_ATT_COMPONENT);
                        final Uri conditionId = safeUri(parser, CONDITION_ATT_ID);
                        if (component != null && conditionId != null) {
                            conditionComponents.add(component);
                            conditionIds.add(conditionId);
                        }
                    } else if (EXIT_CONDITION_TAG.equals(tag)) {
                        rt.exitCondition = readConditionXml(parser);
                        if (rt.exitCondition != null) {
                            rt.exitConditionComponent =
                                    safeComponentName(parser, EXIT_CONDITION_ATT_COMPONENT);
                        }
                    }
                }
            }
            throw new IllegalStateException("Failed to reach END_DOCUMENT");
        }
    }

    public interface Migration {
        ZenModeConfig migrate(XmlV1 v1);
    }

}
