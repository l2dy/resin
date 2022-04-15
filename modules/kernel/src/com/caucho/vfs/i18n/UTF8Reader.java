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

package com.caucho.vfs.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.logging.Logger;

/**
 * Implements an encoding reader for UTF8.
 */
public class UTF8Reader extends EncodingReader {
  private static final Logger log
    = Logger.getLogger(UTF8Reader.class.getName());
  
  private static final char ERROR = 0xfffd;
  
  private InputStream _is;
  
  private int _peek = -1;
  
  private int _ch1 = -1;
  private int _ch2 = -1;
  private int _ch3 = -1;

  /**
   * Null-arg constructor for instantiation by com.caucho.vfs.Encoding only.
   */
  public UTF8Reader()
  {
  }

  /**
   * Create a UTF-8 reader based on the readStream.
   */
  private UTF8Reader(InputStream is)
  {
    _is = is;
  }

  /**
   * Create a UTF-8 reader based on the readStream.
   *
   * @param is the input stream providing the bytes.
   * @param javaEncoding the JDK name for the encoding.
   *
   * @return the UTF-8 reader.
   */
  public Reader create(InputStream is, String javaEncoding)
  {
    return new UTF8Reader(is);
  }

  /**
   * Reads into a character buffer using the correct encoding.
   */
  @Override
  public int read()
    throws IOException
  {
    if (_peek >= 0) {
      int peek = _peek;
      _peek = -1;
      return peek;
    }

    int ch1 = isRead();

    if (ch1 < 0x80) {
      return ch1;
    }
    
    if ((ch1 & 0xe0) == 0xc0) {
      int ch2 = isRead();
      if (ch2 < 0) {
        // jsp/1dit
        // return error("unexpected end of file in utf8 character");
        _ch1 = ch1;
        
        return -1;
      }
      else if ((ch2 & 0xc0) != 0x80) {
        return error("utf-8 character conversion error for '{0}' because second byte is invalid at "
                     + String.format("0x%02x 0x%02x", ch1, ch2));
      }
      
      return ((ch1 & 0x1f) << 6) + (ch2 & 0x3f);
    }
    else if ((ch1 & 0xf0) == 0xe0) {
      int ch2 = isRead();
      int ch3 = isRead();
      
      if (ch2 < 0) {
        // jsp/1dit
        // return error("unexpected end of file in utf8 character");
        
        _ch1 = ch1;
        _ch2 = ch2;
        
        return -1;
      }
      else if ((ch2 & 0xc0) != 0x80) {
        return error("illegal utf8 encoding at "
                     + "\\x" + Integer.toHexString(ch1)
                     + "\\x" + Integer.toHexString(ch2)
                     + "\\x" + Integer.toHexString(ch3));
      }
      
      if (ch3 < 0) {
        _ch1 = ch1;
        _ch2 = ch2;

        return -1;
      }
      else if ((ch3 & 0xc0) != 0x80)
        return error("illegal utf8 encoding at "
                     + "\\x" + Integer.toHexString(ch1)
                     + "\\x" + Integer.toHexString(ch2)
                     + "\\x" + Integer.toHexString(ch3));

      int ch = ((ch1 & 0x1f) << 12) + ((ch2 & 0x3f) << 6) + (ch3 & 0x3f);

      if (ch == 0xfeff) { // If byte-order-mark, read next character
        // server/1m00
        return read();
      }
      else
        return ch;
    }
    else if ((ch1 & 0xf0) == 0xf0) {
      int ch2 = isRead();
      int ch3 = isRead();
      int ch4 = isRead();

      if (ch2 < 0) {
        // jsp/1dit
        // return error("unexpected end of file in utf8 character");
        
        _ch1 = ch1;
        _ch2 = ch2;
        _ch3 = ch3;
        
        return -1;
      }
      else if ((ch2 & 0xc0) != 0x80)
        return error("illegal utf8 encoding at 0x" +
                     Integer.toHexString(ch2));
      
      if (ch3 < 0)
        return error("unexpected end of file in utf8 character");
      else if ((ch3 & 0xc0) != 0x80)
        return error("illegal utf8 encoding at 0x" +
                                          Integer.toHexString(ch3));
      
      if (ch4 < 0)
        return error("unexpected end of file in utf8 character");
      else if ((ch4 & 0xc0) != 0x80)
        return error("illegal utf8 encoding at 0x"
                                          + Integer.toHexString(ch4));
      
      int ch = (((ch1 & 0xf) << 18) +
          ((ch2 & 0x3f) << 12) +
          ((ch3 & 0x3f) << 6) +
          ((ch4 & 0x3f)));

      _peek = 0xdc00 + (ch & 0x3ff);
      
      return 0xd800 + ((ch - 0x10000) / 0x400);
    }
    else
      return error("illegal utf8 encoding at (0x"
                   + Integer.toHexString(ch1) + ")");
  }

  /**
   * Reads into a character buffer using the correct encoding.
   *
   * @param cbuf character buffer receiving the data.
   * @param off starting offset into the buffer.
   * @param len number of characters to read.
   *
   * @return the number of characters read or -1 on end of file.
   */
  @Override
  public int read(char []cbuf, int off, int len)
    throws IOException
  {
    int i = 0;

    InputStream is = _is;
    if (is == null)
      return -1;
    
    for (i = 0; i < len; i++) {
      if (i > 0 && is.available() < 1)
        return i;
      
      int ch = read();

      if (ch < 0)
        return i == 0 ? -1 : i;

      cbuf[off + i] = (char) ch;
    }

    return i;
  }
  
  private int isRead()
    throws IOException
  {
    int ch = _ch1;
    
    if (ch >= 0) {
      _ch1 = _ch2;
      _ch2 = _ch3;
      _ch3 = -1;
      
      return ch;
    }
    else {
      return _is.read();
    }
  }
  
  private char error(String msg)
  {
    log.fine(msg);
    
    return ERROR;
  }
}
