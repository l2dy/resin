/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
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

package com.caucho.jmtp;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.bam.BamError;
import com.caucho.bam.ProtocolException;
import com.caucho.bam.broker.AbstractBroker;
import com.caucho.json.JsonOutput;
import com.caucho.util.Utf8;

/**
 * JmtpWriteStream writes JMTP packets to an OutputStream.
 */
public class JmtpWriter extends AbstractBroker
{
  private static final Logger log
    = Logger.getLogger(JmtpWriter.class.getName());

  private String _address;

  private OutputStream _os;
  private JsonOutput _out;

  public JmtpWriter()
  {
  }

  public JmtpWriter(OutputStream os)
  {
    init(os);
  }

  public void init(OutputStream os)
  {
    _os = os;

    _out = new JsonOutput(_os);
  }

  /**
   * The address of the stream
   */
  public String getAddress()
  {
    return _address;
  }

  /**
   * The address of the stream
   */
  public void setAddress(String address)
  {
    _address = address;
  }

  //
  // message
  //

  /**
   * JMTP unidirectional message
   *
   * <code><pre>
   * message
   * to@to-host.com
   * from@from-host.com
   * message-type
   * json-payload
   * </pre></code>
   */
  public void message(String to, String from, Serializable value)
  {
    try {
      OutputStream os = _os;
      JsonOutput out = _out;

      if (out != null) {
        if (log.isLoggable(Level.FINER)) {
          log.finer(this + " message " + value
                    + " {to:" + to + ", from:" + from + "}");
        }

        os.write(0x00);
        writeString(os, "message\n");
        writeString(os, to);
        os.write('\n');
        writeString(os, from);
        os.write('\n');

        writeType(os, value);

        out.writeObject(value);
        out.flushBuffer();

        os.write(0xff);

        os.flush();
      }
    } catch (IOException e) {
      close();

      throw new ProtocolException(e);
    }
  }

  /**
   * JMTP unidirectional message
   *
   * <code><pre>
   * message
   * to@to-host.com
   * from@from-host.com
   * message-type
   * json-error
   * </pre></code>
   */
  public void messageError(String to,
                           String from,
                           Serializable value,
                           BamError error)
  {
    try {
      OutputStream os = _os;
      JsonOutput out = _out;

      if (out != null) {
        if (log.isLoggable(Level.FINER)) {
          log.finer(this + " messageError " + value
                    + " {to:" + to + ", from:" + from + "}");
        }

        os.write(0x00);
        writeString(os, "message_error\n");

        writeString(os, to);
        os.write('\n');

        writeString(os, from);
        os.write('\n');

        writeType(os, value);

        out.writeObject(value);
        out.flushBuffer();
        os.write('\n');
        out.writeObject(error);
        out.flushBuffer();
        os.write('\n');

        os.write(0xff);

        os.flush();
      }
    } catch (IOException e) {
      close();

      throw new ProtocolException(e);
    }
  }

  //
  // query
  //

  /**
   * Low-level query get
   */
  public void queryGet(long id,
                              String to,
                              String from,
                              Serializable value)
  {
    try {
      OutputStream os = _os;
      JsonOutput out = _out;

      if (out != null) {
        if (log.isLoggable(Level.FINER)) {
          log.finer(this + " queryGet " + value
                    + " {id: " + id + ", to:" + to + ", from:" + from + "}");
        }

        os.write(0x00);
        writeString(os, "get\n");

        writeString(os, to);
        os.write('\n');

        writeString(os, from);
        os.write('\n');

        writeType(os, value);

        writeString(os, String.valueOf(id));
        os.write('\n');

        out.writeObject(value);
        out.flushBuffer();

        os.write(0xff);

        os.flush();
      }
    } catch (IOException e) {
      close();

      throw new ProtocolException(e);
    }
  }

  /**
   * Low-level query set
   */
  public void querySet(long id,
                              String to,
                              String from,
                              Serializable value)
  {
    try {
      OutputStream os = _os;
      JsonOutput out = _out;

      if (out != null) {
        if (log.isLoggable(Level.FINER)) {
          log.finer(this + " querySet " + value
                    + " {id: " + id + ", to:" + to + ", from:" + from + "}");
        }

        os.write(0x00);
        writeString(os, "set\n");

        writeString(os, to);
        os.write('\n');

        writeString(os, from);
        os.write('\n');

        writeType(os, value);

        writeString(os, String.valueOf(id));
        os.write('\n');

        out.writeObject(value);
        out.flushBuffer();

        os.write(0xff);

        os.flush();
      }
    } catch (IOException e) {
      close();

      throw new ProtocolException(e);
    }
  }

  /**
   * Low-level query response
   */
  public void queryResult(long id,
                              String to,
                              String from,
                              Serializable value)
  {
    try {
      OutputStream os = _os;
      JsonOutput out = _out;

      if (out != null) {
        if (log.isLoggable(Level.FINER)) {
          log.finer(this + " queryResult " + value
                    + " {id: " + id + ", to:" + to + ", from:" + from + "}");
        }

        os.write(0x00);
        writeString(os, "result\n");

        writeString(os, to);
        os.write('\n');

        writeString(os, from);
        os.write('\n');

        writeType(os, value);

        writeString(os, String.valueOf(id));
        os.write('\n');

        out.writeObject(value);
        out.flushBuffer();

        os.write(0xff);

        os.flush();
      }
    } catch (IOException e) {
      close();

      throw new ProtocolException(e);
    }
  }

  /**
   * Low-level query error
   */
  public void queryError(long id,
                         String to,
                         String from,
                         Serializable value,
                         BamError error)
  {
    try {
      OutputStream os = _os;
      JsonOutput out = _out;

      if (out != null) {
        if (log.isLoggable(Level.FINER)) {
          log.finer(this + " queryError " + error + " " + value
                    + " {id: " + id + ", to:" + to + ", from:" + from + "}");
        }

        os.write(0x00);
        writeString(os, "query_error\n");

        writeString(os, to);
        os.write('\n');

        writeString(os, from);
        os.write('\n');

        writeType(os, value);

        writeString(os, String.valueOf(id));
        os.write('\n');

        out.writeObject(value);
        out.flushBuffer();
        os.write('\n');
        out.writeObject(error);
        out.flushBuffer();
        os.write('\n');

        os.write(0xff);

        os.flush();
      }
    } catch (IOException e) {
      close();

      throw new RuntimeException(e);
    }
  }

  private void writeType(OutputStream os, Object value)
    throws IOException
  {
    if (value == null) {
      writeString(os, "null\n");
      return;
    }

    Class<?> cl = value.getClass();

    if (cl == String.class) {
      writeString(os, "String\n");
    }
    else if (cl.getName().startsWith("java.")) {
      writeString(os, "Object\n");
    }
    else {
      writeString(os, value.getClass().getName());
      os.write('\n');
    }
  }

  private void writeString(OutputStream os, String s)
    throws IOException
  {
    Utf8.write(os, s);
  }

  public void flush()
    throws IOException
  {
    JsonOutput out = _out;

    if (out != null) {
      out.flush();
    }
  }

  public boolean isClosed()
  {
    return _out == null;
  }

  public void close()
  {
    if (log.isLoggable(Level.FINER))
      log.finer(this + " close");

    try {
      JsonOutput out = _out;
      _out = null;

      if (out != null)
        out.close();
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getAddress() + "]";
  }
}
