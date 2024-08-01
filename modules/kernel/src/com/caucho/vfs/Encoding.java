/*
 * Copyright (c) 1998-2018 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.vfs;

import com.caucho.util.CharBuffer;
import com.caucho.util.LruCache;
import com.caucho.vfs.i18n.EncodingReader;
import com.caucho.vfs.i18n.EncodingWriter;
import com.caucho.vfs.i18n.ISO8859_1Writer;
import com.caucho.vfs.i18n.JDKReader;
import com.caucho.vfs.i18n.JDKWriter;

import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts between the mime encoding names and Java encoding names.
 */
public class Encoding {
  private static HashMap<String,String> _javaNameStatic;
  private static HashMap<String,String> _mimeNameStatic;
  private static HashMap<String,String> _localeNameStatic;

  private static LruCache<String,String> _javaName
      = new LruCache<String,String>(4096);
  private static LruCache<String,String> _mimeName
      = new LruCache<String,String>(4096);

  // map from an encoding name to its EncodingReader factory.
  static final LruCache<String,EncodingReader> _readEncodingFactories
    = new LruCache<String,EncodingReader>(4096);

  // map from an encoding name to its EncodingWriter factory.
  static final LruCache<String,EncodingWriter> _writeEncodingFactories
    = new LruCache<String,EncodingWriter>(4096);

  static final EncodingWriter _latin1Writer = new ISO8859_1Writer();

  /**
   * Can't create an instance of the encoding class.
   */
  private Encoding() {}

  /**
   * Returns the canonical mime name for the given character encoding.
   *
   * @param encoding character encoding name, possibly an alias
   *
   * @return canonical mime name for the encoding.
   */
  public static String getMimeName(String encoding)
  {
    if (encoding == null)
      return null;

    String value = _mimeName.get(encoding);
    if (value != null)
      return value;

    String upper = normalize(encoding);

    String lookup = _mimeNameStatic.get(upper);

    value = lookup == null ? upper : lookup;

    _mimeName.put(encoding, value);

    return value;
  }

  /**
   * Returns the canonical mime name for the given locale.
   *
   * @param locale locale to use.
   *
   * @return canonical mime name for the encoding.
   */
  public static String getMimeName(Locale locale)
  {
    if (locale == null)
      return "utf-8";

    String mimeName = _localeNameStatic.get(locale.toString());

    if (mimeName == null)
      mimeName = _localeNameStatic.get(locale.getLanguage());

    if (mimeName == null)
      return "utf-8";
    else
      return mimeName;
  }

  /**
   * Returns a Reader to translate bytes to characters.  If a specialized
   * reader exists in com.caucho.vfs.i18n, use it.
   *
   * @param is the input stream.
   * @param encoding the encoding name.
   *
   * @return a reader for the translation
   */
  public static Reader getReadEncoding(InputStream is, String encoding)
    throws UnsupportedEncodingException
  {
    return getReadFactory(encoding).create(is);
  }

  /**
   * Returns a Reader to translate bytes to characters.  If a specialized
   * reader exists in com.caucho.vfs.i18n, use it.
   *
   * @param is the input stream.
   * @param encoding the encoding name.
   *
   * @return a reader for the translation
   */
  public static EncodingReader getReadFactory(final String encoding)
    throws UnsupportedEncodingException
  {
    String encKey = encoding == null ? "iso-8859-1" : encoding;

    EncodingReader factory = _readEncodingFactories.get(encKey);

    if (factory == null) {
      try {
        String javaEncoding = Encoding.getJavaName(encoding);

        if (javaEncoding == null)
          javaEncoding = "ISO8859_1";

        String className = "com.caucho.vfs.i18n." + javaEncoding + "Reader";

        Class cl = Class.forName(className);

        factory = (EncodingReader) cl.newInstance();
        factory.setJavaEncoding(javaEncoding);
      } catch (Throwable e) {
      }

      if (factory == null) {
        String javaEncoding = Encoding.getJavaName(encoding);

        if (javaEncoding == null)
          javaEncoding = "ISO8859_1";

        factory = new JDKReader();
        factory.setJavaEncoding(javaEncoding);
      }

      _readEncodingFactories.put(encKey, factory);
    }

    return factory;
  }

  /**
   * Returns an EncodingWriter to translate characters to bytes.
   *
   * @param encoding the encoding name.
   *
   * @return a writer for the translation
   */
  public static EncodingWriter getWriteEncoding(String encoding)
  {
    if (encoding == null)
      encoding = "iso-8859-1";

    EncodingWriter factory = _writeEncodingFactories.get(encoding);

    if (factory != null)
      return factory.create();

    factory = _writeEncodingFactories.get(encoding);

    if (factory == null) {
      try {
        String javaEncoding = Encoding.getJavaName(encoding);

        if (javaEncoding == null)
          javaEncoding = "ISO8859_1";

        String className = "com.caucho.vfs.i18n." + javaEncoding + "Writer";

        Class cl = Class.forName(className);

        factory = (EncodingWriter) cl.newInstance();
        factory.setJavaEncoding(javaEncoding);
      } catch (Throwable e) {
      }

      if (factory == null) {
        factory = new JDKWriter();
        String javaEncoding = Encoding.getJavaName(encoding);

        if (javaEncoding == null)
          javaEncoding = "ISO8859_1";
        factory.setJavaEncoding(javaEncoding);
      }

      _writeEncodingFactories.put(encoding, factory);
    }

    // return factory.create(factory.getJavaEncoding());
    // charset uses the original encoding, not the java encoding
    return factory.create(encoding);
  }

  /**
   * Returns the latin 1 writer.
   */
  public static EncodingWriter getLatin1Writer()
  {
    return _latin1Writer;
  }

  /**
   * Returns the Java name for the given encoding.
   *
   * @param encoding character encoding name
   *
   * @return Java encoding name
   */
  public static String getJavaName(String encoding)
  {
    if (encoding == null)
      return null;
    
    String javaName = _javaName.get(encoding);
    
    if (javaName != null)
      return javaName;

    String upper = normalize(encoding);

    javaName = _javaNameStatic.get(upper);
    if (javaName == null) {
      String lookup = _mimeNameStatic.get(upper);

      if (lookup != null)
        javaName = _javaNameStatic.get(lookup);
    }
    
    if (javaName == null)
      javaName = upper;
    
    _javaName.put(encoding, javaName);

    return javaName;
  }

  /**
   * Returns the Java name for the given locale.
   *
   * @param locale the locale to use
   *
   * @return Java encoding name
   */
  public static String getJavaName(Locale locale)
  {
    if (locale == null)
      return null;

    return getJavaName(getMimeName(locale));
  }

  /**
   * Normalize the user's encoding name to avoid case issues.
   */
  private static String normalize(String name)
  {
    CharBuffer cb = new CharBuffer();

    int len = name.length();
    for (int i = 0; i < len; i++) {
      char ch = name.charAt(i);

      if (Character.isLowerCase(ch))
        cb.append(Character.toUpperCase(ch));
      else if (ch == '_')
        cb.append('-');
      else
        cb.append(ch);
    }

    return cb.close();
  }


  static {
    _javaNameStatic = new HashMap<String,String>();
    _mimeNameStatic = new HashMap<String,String>();
    _localeNameStatic = new HashMap<String,String>();

    _mimeNameStatic.put("ANSI-X3.4-1968", "US-ASCII");
    _mimeNameStatic.put("ISO-IR-6", "US-ASCII");
    _mimeNameStatic.put("ISO-646.IRV:1991", "US-ASCII");
    _mimeNameStatic.put("ASCII", "US-ASCII");
    _mimeNameStatic.put("ISO646-US", "US-ASCII");
    _mimeNameStatic.put("US-ASCII", "US-ASCII");
    _mimeNameStatic.put("us", "US-ASCII");
    _mimeNameStatic.put("IBM367", "US-ASCII");
    _mimeNameStatic.put("CP367", "US-ASCII");
    _mimeNameStatic.put("CSASCII", "US-ASCII");
    _javaNameStatic.put("US-ASCII", "ISO8859_1");

    _mimeNameStatic.put("ISO-2022-KR", "ISO-2022-KR");
    _mimeNameStatic.put("CSISO2022KR", "ISO-2022-KR");
    _mimeNameStatic.put("ISO2022-KR", "ISO-2022-KR");
    _javaNameStatic.put("ISO-2022-KR", "ISO2022_KR");

    _mimeNameStatic.put("EUC-KR", "EUC-KR");
    _mimeNameStatic.put("CSEUCKR", "EUC-KR");
    _javaNameStatic.put("EUC-KR", "EUC_KR");

    _mimeNameStatic.put("ISO-2022-JP", "ISO-2022-JP");
    _mimeNameStatic.put("CSISO2022JP", "ISO-2022-JP");
    _mimeNameStatic.put("ISO2022-JP", "ISO-2022-JP");
    _javaNameStatic.put("ISO-2022-JP", "ISO2022JP");

    _mimeNameStatic.put("ISO-2022-JP-2", "ISO-2022-JP-2");
    _mimeNameStatic.put("CSISO2022JP2", "ISO-2022-JP-2");
    _mimeNameStatic.put("ISO2022-JP2", "ISO-2022-JP-2");
    _javaNameStatic.put("ISO-2022-JP-2", "ISO2022_JP2");

    _mimeNameStatic.put("ISO_8859-1:1987", "ISO-8859-1");
    _mimeNameStatic.put("ISO-IR-100", "ISO-8859-1");
    _mimeNameStatic.put("ISO-8859-1", "ISO-8859-1");
    _mimeNameStatic.put("LATIN1", "ISO-8859-1");
    _mimeNameStatic.put("LATIN-1", "ISO-8859-1");
    _mimeNameStatic.put("L1", "ISO-8859-1");
    _mimeNameStatic.put("IBM819", "ISO-8859-1");
    _mimeNameStatic.put("CP819", "ISO-8859-1");
    _mimeNameStatic.put("CSISOLATIN1", "ISO-8859-1");
    _mimeNameStatic.put("ISO8859-1", "ISO-8859-1");
    _mimeNameStatic.put("8859-1", "ISO-8859-1");
    _mimeNameStatic.put("8859_1", "ISO-8859-1");
    _javaNameStatic.put("ISO-8859-1", "ISO8859_1");

    _mimeNameStatic.put("ISO-8859-2:1987", "ISO-8859-2");
    _mimeNameStatic.put("ISO-IR-101", "ISO-8859-2");
    _mimeNameStatic.put("ISO-8859-2", "ISO-8859-2");
    _mimeNameStatic.put("LATIN2", "ISO-8859-2");
    _mimeNameStatic.put("LATIN-2", "ISO-8859-2");
    _mimeNameStatic.put("L2", "ISO-8859-2");
    _mimeNameStatic.put("CSISOLATIN2", "ISO-8859-2");
    _mimeNameStatic.put("ISO8859-2", "ISO-8859-2");
    _javaNameStatic.put("ISO-8859-2", "ISO8859_2");

    _mimeNameStatic.put("ISO-8859-3:1988", "ISO-8859-3");
    _mimeNameStatic.put("ISO-IR-109", "ISO-8859-3");
    _mimeNameStatic.put("ISO-8859-3", "ISO-8859-3");
    _mimeNameStatic.put("ISO-8859-3", "ISO-8859-3");
    _mimeNameStatic.put("LATIN3", "ISO-8859-3");
    _mimeNameStatic.put("LATIN-3", "ISO-8859-3");
    _mimeNameStatic.put("L3", "ISO-8859-3");
    _mimeNameStatic.put("CSISOLATIN3", "ISO-8859-3");
    _mimeNameStatic.put("ISO8859-3", "ISO-8859-3");
    _javaNameStatic.put("ISO-8859-3", "ISO8859_3");

    _mimeNameStatic.put("ISO-8859-4:1988", "ISO-8859-4");
    _mimeNameStatic.put("ISO-IR-110", "ISO-8859-4");
    _mimeNameStatic.put("ISO-8859-4", "ISO-8859-4");
    _mimeNameStatic.put("ISO-8859-4", "ISO-8859-4");
    _mimeNameStatic.put("LATIN4", "ISO-8859-4");
    _mimeNameStatic.put("LATIN-4", "ISO-8859-4");
    _mimeNameStatic.put("L4", "ISO-8859-4");
    _mimeNameStatic.put("CSISOLATIN4", "ISO-8859-4");
    _mimeNameStatic.put("ISO8859-4", "ISO-8859-4");
    _javaNameStatic.put("ISO-8859-4", "ISO8859_4");

    _mimeNameStatic.put("ISO-8859-5:1988", "ISO-8859-5");
    _mimeNameStatic.put("ISO-IR-144", "ISO-8859-5");
    _mimeNameStatic.put("ISO-8859-5", "ISO-8859-5");
    _mimeNameStatic.put("ISO-8859-5", "ISO-8859-5");
    _mimeNameStatic.put("CYRILLIC", "ISO-8859-5");
    _mimeNameStatic.put("CSISOLATINCYRILLIC", "ISO-8859-5");
    _mimeNameStatic.put("ISO8859-5", "ISO-8859-5");
    _javaNameStatic.put("ISO-8859-5", "ISO8859_5");

    _mimeNameStatic.put("ISO-8859-6:1987", "ISO-8859-6");
    _mimeNameStatic.put("ISO-IR-127", "ISO-8859-6");
    _mimeNameStatic.put("ISO-8859-6", "ISO-8859-6");
    _mimeNameStatic.put("ISO-8859-6", "ISO-8859-6");
    _mimeNameStatic.put("ECMA-114", "ISO-8859-6");
    _mimeNameStatic.put("ASMO-708", "ISO-8859-6");
    _mimeNameStatic.put("ARABIC", "ISO-8859-6");
    _mimeNameStatic.put("CSISOLATINARABIC", "ISO-8859-6");
    _mimeNameStatic.put("ISO8859-6", "ISO-8859-6");
    _javaNameStatic.put("ISO-8859-6", "ISO8859_6");

    _mimeNameStatic.put("ISO-8859-7:1987", "ISO-8859-7");
    _mimeNameStatic.put("ISO-IR-126", "ISO-8859-7");
    _mimeNameStatic.put("ISO-8859-7", "ISO-8859-7");
    _mimeNameStatic.put("ISO-8859-7", "ISO-8859-7");
    _mimeNameStatic.put("ELOT-928", "ISO-8859-7");
    _mimeNameStatic.put("ECMA-118", "ISO-8859-7");
    _mimeNameStatic.put("GREEK", "ISO-8859-7");
    _mimeNameStatic.put("GREEK8", "ISO-8859-7");
    _mimeNameStatic.put("CSISOLATINGREEN", "ISO-8859-7");
    _mimeNameStatic.put("ISO8859-7", "ISO-8859-7");
    _javaNameStatic.put("ISO-8859-7", "ISO8859_7");

    _mimeNameStatic.put("ISO-8859-8:1988", "ISO-8859-8");
    _mimeNameStatic.put("ISO-IR-138", "ISO-8859-8");
    _mimeNameStatic.put("ISO-8859-8", "ISO-8859-8");
    _mimeNameStatic.put("ISO-8859-8", "ISO-8859-8");
    _mimeNameStatic.put("HEBREW", "ISO-8859-8");
    _mimeNameStatic.put("CSISOLATINHEBREW", "ISO-8859-8");
    _mimeNameStatic.put("ISO8859-8", "ISO-8859-8");
    _javaNameStatic.put("ISO-8859-8", "ISO8859_8");

    _mimeNameStatic.put("ISO-8859-9:1989", "ISO-8859-9");
    _mimeNameStatic.put("ISO-IR-148", "ISO-8859-9");
    _mimeNameStatic.put("ISO-8859-9", "ISO-8859-9");
    _mimeNameStatic.put("ISO-8859-9", "ISO-8859-9");
    _mimeNameStatic.put("LATIN5", "ISO-8859-9");
    _mimeNameStatic.put("LATIN-5", "ISO-8859-9");
    _mimeNameStatic.put("L5", "ISO-8859-9");
    _mimeNameStatic.put("CSISOLATIN5", "ISO-8859-9");
    _mimeNameStatic.put("ISO8859-9", "ISO-8859-9");
    _javaNameStatic.put("ISO-8859-9", "ISO8859_9");

    /* unsupported by java
    _mimeNameStatic.put("ISO_8859-10:1992", "ISO-8859-10");
    _mimeNameStatic.put("iso-ir-157", "ISO-8859-10");
    _mimeNameStatic.put("I6", "ISO-8859-10");
    _mimeNameStatic.put("cslSOLatin6", "ISO-8859-10");
    _mimeNameStatic.put("latin6", "ISO-8859-10");
    _javaNameStatic.put("ISO-8859-10", "ISO8859_10");
    */

    _mimeNameStatic.put("UTF-7", "UTF-7");
    _mimeNameStatic.put("UTF7", "UTF-7");
    _javaNameStatic.put("UTF-7", "UTF7");

    _mimeNameStatic.put("UTF-8", "utf-8");
    _mimeNameStatic.put("UTF8", "utf-8");
    _javaNameStatic.put("UTF-8", "UTF8");

    _mimeNameStatic.put("UTF-16", "utf-16");
    _mimeNameStatic.put("UTF16", "utf-16");
    _javaNameStatic.put("UTF-16", "UTF16");

    _mimeNameStatic.put("UTF-16-REV", "utf-16-rev");
    _mimeNameStatic.put("UTF16-REV", "utf-16-rev");
    _javaNameStatic.put("utf-16-rev", "UTF16_REV");

    _mimeNameStatic.put("JIS-ENCODING", "JIS_Encoding");
    _mimeNameStatic.put("JIS-ENCODING", "JIS_Encoding");
    _mimeNameStatic.put("CSJISENCODING", "JIS_Encoding");
    _javaNameStatic.put("JIS_Encoding", "JIS_ENCODING");

    _mimeNameStatic.put("SHIFT-JIS", "Shift_JIS");
    _mimeNameStatic.put("SHIFT_JIS", "Shift_JIS");
    _mimeNameStatic.put("CSSHIFTJIS", "Shift_JIS");
    _mimeNameStatic.put("SJIS", "Shift_JIS");
    _javaNameStatic.put("Shift_JIS", "SJIS");

    _mimeNameStatic.put("EUC-JP", "EUC-JP");
    _mimeNameStatic.put("EUC-JP", "EUC-JP");
    _mimeNameStatic.put("EUCJP", "EUC-JP");
    _mimeNameStatic.put("EUC-JP-LINUX", "EUC-JP");
    _javaNameStatic.put("EUC-JP", "EUC_JP");

    _mimeNameStatic.put("GB2312", "GB2312");
    _mimeNameStatic.put("CSGB2312", "GB2312");
    _javaNameStatic.put("GB2312", "GB2312");

    _mimeNameStatic.put("GBK", "GBK");
    _javaNameStatic.put("GBK", "GBK");

    _mimeNameStatic.put("BIG5", "Big5");
    _mimeNameStatic.put("BIG-5", "Big5");
    _mimeNameStatic.put("CSBIG5", "Big5");
    _javaNameStatic.put("Big5", "BIG5");

    _mimeNameStatic.put("KOI8-R", "KOI8-R");
    _mimeNameStatic.put("KOI-8-R", "KOI8-R");
    _mimeNameStatic.put("KOI8-R", "KOI8-R");
    _javaNameStatic.put("KOI8-R", "KOI8-R");

    _mimeNameStatic.put("MS950", "ms950");
    _javaNameStatic.put("ms950", "MS950");

    _javaNameStatic.put("JAVA", "JAVA");

    _mimeNameStatic.put("windows-hack", "ISO-8859-1");
    _mimeNameStatic.put("WINDOWS-HACK", "ISO-8859-1");
    _javaNameStatic.put("WINDOWS-HACK", "WindowsHack");

    // #4180
    _mimeNameStatic.put("MACROMAN", "utf-8");
    _javaNameStatic.put("MacRoman", "utf-8");

    _mimeNameStatic.put("KS_C_5601-1987", "ks_c_5601-1987");
    _javaNameStatic.put("ks_c_5601-1987", "Cp949");

    _javaNameStatic.put("IBM500", "Cp500");

    String []cp = new String[] {
      "037", "1006", "1025", "1026", "1046", "1097",
      "1098", "1112", "1122", "1123", "1124", "1250",
      "1251", "1252", "1253", "1254", "1255", "1256",
      "1257", "1258", "1381", "273", "277", "278", "280", "284",
      "285", "297", "33722", "420", "424", "437", "500", "737",
      "775", "838", "850", "852", "855", "857", "860", "861", "862",
      "863", "864", "865", "866", "868", "869", "870", "871", "874",
      "875", "918", "921", "922", "930", "933", "935", "937", "939",
      "942", "948", "949", "964", "970"
    };

    for (int i = 0; i < cp.length; i++) {
      _mimeNameStatic.put("CP" + cp[i], "windows-" + cp[i]);
      _mimeNameStatic.put("WINDOWS-" + cp[i], "windows-" + cp[i]);
      _javaNameStatic.put("windows-" + cp[i], "Cp" + cp[i]);
    }

    // from http://www.w3c.org/International/O-charset-lang.html
    _localeNameStatic = new HashMap<String,String>();
    _localeNameStatic.put("af", "ISO-8859-1");
    _localeNameStatic.put("sq", "ISO-8859-1");
    _localeNameStatic.put("ar", "ISO-8859-6");
    _localeNameStatic.put("eu", "ISO-8859-1");
    _localeNameStatic.put("bg", "ISO-8859-5");
    _localeNameStatic.put("be", "ISO-8859-5");
    _localeNameStatic.put("ca", "ISO-8859-1");
    _localeNameStatic.put("hr", "ISO-8859-2");
    _localeNameStatic.put("cs", "ISO-8859-2");
    _localeNameStatic.put("da", "ISO-8859-1");
    _localeNameStatic.put("nl", "ISO-8859-1");
    _localeNameStatic.put("en", "ISO-8859-1");
    _localeNameStatic.put("eo", "ISO-8859-3");
    _localeNameStatic.put("et", "ISO-8859-4");
    _localeNameStatic.put("fo", "ISO-8859-1");
    _localeNameStatic.put("fi", "ISO-8859-1");
    _localeNameStatic.put("fr", "ISO-8859-1");
    _localeNameStatic.put("gl", "ISO-8859-1");
    _localeNameStatic.put("de", "ISO-8859-1");
    _localeNameStatic.put("el", "ISO-8859-7");
    _localeNameStatic.put("iw", "ISO-8859-8");
    _localeNameStatic.put("hu", "ISO-8859-2");
    _localeNameStatic.put("is", "ISO-8859-1");
    _localeNameStatic.put("ga", "ISO-8859-1");
    _localeNameStatic.put("it", "ISO-8859-1");
    _localeNameStatic.put("ja", "Shift_JIS");
    _localeNameStatic.put("lv", "ISO-8859-4");
    _localeNameStatic.put("lt", "ISO-8859-4");
    _localeNameStatic.put("mk", "ISO-8859-5");
    _localeNameStatic.put("mt", "ISO-8859-3");
    _localeNameStatic.put("no", "ISO-8859-1");
    _localeNameStatic.put("pl", "ISO-8859-2");
    _localeNameStatic.put("pt", "ISO-8859-1");
    _localeNameStatic.put("ro", "ISO-8859-2");
    // _localeNameStatic.put("ru", "KOI8-R");
    _localeNameStatic.put("ru", "ISO-8859-5");
    _localeNameStatic.put("gd", "ISO-8859-1");
    _localeNameStatic.put("sr", "ISO-8859-5");
    _localeNameStatic.put("sk", "ISO-8859-2");
    _localeNameStatic.put("sl", "ISO-8859-2");
    _localeNameStatic.put("es", "ISO-8859-1");
    _localeNameStatic.put("sv", "ISO-8859-1");
    _localeNameStatic.put("tr", "ISO-8859-9");
    _localeNameStatic.put("uk", "ISO-8859-5");

    _localeNameStatic.put("ko", "EUC-KR");
    _localeNameStatic.put("zh", "GB2312");
    _localeNameStatic.put("zh_TW", "Big5");
  }
}
