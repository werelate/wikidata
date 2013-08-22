package org.werelate.source;

import java.util.regex.Pattern;

public class SourceUtil
{
   public static final Pattern REDIRECT_PATTERN = Pattern.compile("#redirect\\s*\\[\\[(.*?)\\]\\]", Pattern.CASE_INSENSITIVE);
   public static final Pattern TITLENO = Pattern.compile("www\\.familysearch\\.org.*?titleno=([0123456789]+)");
   public static final Pattern START = Pattern.compile("<source id=\"([0-9]+)\"\\s*>");
   public static final Pattern END = Pattern.compile("</source>");
   public static final Pattern CATEGORY = Pattern.compile("\\[\\[Category:([^\\]]*)\\]\\]");
}
