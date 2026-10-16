package com.xjtu.iron.foundation.core.text;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Iron Foundation 字符串工具门面。
 *
 * <p>命名为 {@code IronStrings} 而不是 {@code StringUtils}，是为了避免与
 * Apache Commons Lang、Spring Framework、Hutool 等常见工具类产生同名导入冲突。
 * 该类只对项目内高频、语义需要统一的方法做薄封装，不复制 Commons Lang 的全部 API。</p>
 *
 * <p>本类不负责 SQL、HTML、XML、JSON 转义，也不承担模板引擎职责；这些能力应由更专门的组件处理。</p>
 */
public final class IronStrings {

    private static final Pattern LINE_ENDING_PATTERN = Pattern.compile("\\r\\n|\\r");
    private static final Pattern MULTI_WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private IronStrings() {
    }

    /** 判断字符串是否为 null、空串或全空白字符。 */
    public static boolean isBlank(String value) {
        return org.apache.commons.lang3.StringUtils.isBlank(value);
    }

    /** 判断字符串是否包含至少一个非空白字符。 */
    public static boolean isNotBlank(String value) {
        return org.apache.commons.lang3.StringUtils.isNotBlank(value);
    }

    /** 判断字符串是否为 null 或空串；不把空白字符串当作空。 */
    public static boolean isEmpty(String value) {
        return org.apache.commons.lang3.StringUtils.isEmpty(value);
    }

    /** 判断字符串是否不为 null 且长度大于 0；空白字符串也算非空。 */
    public static boolean isNotEmpty(String value) {
        return org.apache.commons.lang3.StringUtils.isNotEmpty(value);
    }

    /** 去除首尾空白；如果结果为空白，则返回 null。 */
    public static String trimToNull(String value) {
        return org.apache.commons.lang3.StringUtils.trimToNull(value);
    }

    /** 去除首尾空白；如果输入为 null，则返回空串。 */
    public static String trimToEmpty(String value) {
        return org.apache.commons.lang3.StringUtils.trimToEmpty(value);
    }

    /** 当输入为空白时返回默认值，否则返回去除首尾空白后的原值。 */
    public static String defaultIfBlank(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    /** 当输入为 null 或空串时返回默认值，不把空白字符串当作默认值。 */
    public static String defaultIfEmpty(String value, String defaultValue) {
        return org.apache.commons.lang3.StringUtils.defaultIfEmpty(value, defaultValue);
    }

    /** 忽略大小写比较两个字符串，两个 null 视为相等。 */
    public static boolean equalsIgnoreCase(String left, String right) {
        return org.apache.commons.lang3.StringUtils.equalsIgnoreCase(left, right);
    }

    /** 区分大小写比较两个字符串，两个 null 视为相等。 */
    public static boolean equals(String left, String right) {
        return Objects.equals(left, right);
    }

    /** 判断字符串是否以指定前缀开始，支持 null 安全判断。 */
    public static boolean startsWith(String value, String prefix) {
        return org.apache.commons.lang3.StringUtils.startsWith(value, prefix);
    }

    /** 判断字符串是否以指定后缀结束，支持 null 安全判断。 */
    public static boolean endsWith(String value, String suffix) {
        return org.apache.commons.lang3.StringUtils.endsWith(value, suffix);
    }

    /** 如果字符串存在指定前缀，则移除一次；否则返回原值。 */
    public static String removeStart(String value, String prefix) {
        return org.apache.commons.lang3.StringUtils.removeStart(value, prefix);
    }

    /** 如果字符串存在指定后缀，则移除一次；否则返回原值。 */
    public static String removeEnd(String value, String suffix) {
        return org.apache.commons.lang3.StringUtils.removeEnd(value, suffix);
    }

    /** 判断字符串是否包含指定片段，支持 null 安全判断。 */
    public static boolean contains(String value, String search) {
        return org.apache.commons.lang3.StringUtils.contains(value, search);
    }

    /** 将 Windows 和旧 Mac 换行统一为 Unix 换行符 \n。 */
    public static String normalizeLineEndings(String value) {
        if (value == null) {
            return null;
        }
        return LINE_ENDING_PATTERN.matcher(value).replaceAll("\n");
    }

    /** 将连续空白字符折叠为一个空格，并去除首尾空白。 */
    public static String collapseWhitespace(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = MULTI_WHITESPACE_PATTERN.matcher(value).replaceAll(" ");
        return collapsed.trim();
    }

    /** 返回字符串 Unicode 码点数量；null 返回 0。 */
    public static int codePointLength(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    /** 按 Unicode 码点安全截断，避免把 emoji 等代理对字符截断成非法半个字符。 */
    public static String truncate(String value, int maxCodePoints) {
        if (value == null) {
            return null;
        }
        if (maxCodePoints < 0) {
            throw new IllegalArgumentException("maxCodePoints must not be negative");
        }
        if (codePointLength(value) <= maxCodePoints) {
            return value;
        }
        int endIndex = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, endIndex);
    }

    /** 按 Unicode 码点安全截断，并在发生截断时追加后缀。 */
    public static String truncateWithSuffix(String value, int maxCodePoints, String suffix) {
        if (value == null) {
            return null;
        }
        if (maxCodePoints < 0) {
            throw new IllegalArgumentException("maxCodePoints must not be negative");
        }
        String actualSuffix = suffix == null ? "" : suffix;
        if (codePointLength(value) <= maxCodePoints) {
            return value;
        }
        int suffixLength = codePointLength(actualSuffix);
        if (suffixLength >= maxCodePoints) {
            return truncate(actualSuffix, maxCodePoints);
        }
        return truncate(value, maxCodePoints - suffixLength) + actualSuffix;
    }

    /** 将驼峰命名转换为下划线小写命名。 */
    public static String camelToSnake(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0 && builder.length() > 0 && builder.charAt(builder.length() - 1) != '_') {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(ch));
            } else if (ch == '-' || Character.isWhitespace(ch)) {
                builder.append('_');
            } else {
                builder.append(ch);
            }
        }
        return builder.toString().replaceAll("_+", "_");
    }

    /** 将下划线或短横线命名转换为小驼峰命名。 */
    public static String snakeToCamel(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.toLowerCase(Locale.ROOT).split("[_-]+");
        if (parts.length == 0) {
            return value;
        }
        StringBuilder builder = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                builder.append(Character.toUpperCase(parts[i].charAt(0)));
                builder.append(parts[i].substring(1));
            }
        }
        return builder.toString();
    }

    /** 按分隔符拆分字符串，自动去除空白项和空字符串。 */
    public static List<String> splitToList(String value, String delimiter) {
        if (isBlank(value)) {
            return Collections.emptyList();
        }
        Objects.requireNonNull(delimiter, "delimiter must not be null");
        return Arrays.stream(value.split(Pattern.quote(delimiter)))
                .map(IronStrings::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
    }

    /** 使用分隔符拼接字符串，自动跳过 null 元素。 */
    public static String join(Iterable<String> values, String delimiter) {
        if (values == null) {
            return "";
        }
        return org.apache.commons.lang3.StringUtils.join(values, delimiter);
    }

    /** 使用分隔符拼接集合，自动跳过 null 元素。 */
    public static String join(Collection<String> values, String delimiter) {
        return join((Iterable<String>) values, delimiter);
    }
}
