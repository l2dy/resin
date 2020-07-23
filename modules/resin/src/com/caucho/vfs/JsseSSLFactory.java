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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import com.caucho.config.ConfigException;
import com.caucho.env.service.RootDirectorySystem;
import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.caucho.util.IoUtil;
import com.caucho.util.L10N;

/**
 * Abstract socket to handle both normal sockets and bin/resin sockets.
 */
public class JsseSSLFactory implements SSLFactory {
  private static final Logger log
    = Logger.getLogger(JsseSSLFactory.class.getName());
  
  private static final L10N L = new L10N(JsseSSLFactory.class);

  private static Method _honorCipherOrderMethod;
  private static Method _getSSLParametersMethod;
  private static final Method _setSSLParameters;
  
  private Path _keyStoreFile;
  private String _alias;
  private String _password;
  private String _verifyClient;
  
  private String _keyStorePassword;
  private String _keyStoreType = "jks";
  
  private String _keyManagerFactory = "SunX509";
  private String _keyManagerAlgorithm;
  private String _keyManagerProvider;
  
  private Path _trustStoreFile;
  private String _trustStorePassword;
  private String _trustStoreType = "jks";
  private String _trustStoreAlgorithm;
  private String _trustStoreProvider;
    
  private String _sslContext = "TLS";
  private String []_cipherSuites;
  private String []_cipherSuitesForbidden;
  private String []_protocols;

  private String _selfSignedName;

  private Boolean _isHonorCipherOrder;

  private KeyStore _keyStore;
  private KeyStore _trustStore;
  
  /**
   * Creates a ServerSocket factory without initializing it.
   */
  public JsseSSLFactory()
  {
  }

  /**
   * Sets the enabled cipher suites
   */
  public void setCipherSuites(String []ciphers)
  {
    _cipherSuites = ciphers;
  }

  /**
   * Sets the enabled cipher suites
   */
  public void setCipherSuitesForbidden(String []ciphers)
  {
    _cipherSuitesForbidden = ciphers;
  }

  /**
   * Sets the key store
   */
  public void setKeyStoreFile(Path keyStoreFile)
  {
    _keyStoreFile = keyStoreFile;
  }
 

  /**
   * Returns the certificate file.
   */
  public Path getKeyStoreFile()
  {
    return _keyStoreFile;
  }

  /**
   * Sets the password.
   */
  public void setPassword(String password)
  {
    _password = password;
  }

  /**
   * Returns the key file.
   */
  public String getPassword()
  {
    return _password;
  }

  /**
   * Sets the certificate alias
   */
  public void setAlias(String alias)
  {
    _alias = alias;
  }

  /**
   * Returns the alias.
   */
  public String getAlias()
  {
    return _alias;
  }

  /**
   * Sets the verifyClient.
   */
  public void setVerifyClient(String verifyClient)
  {
    _verifyClient = verifyClient;
  }

  /**
   * Returns the key file.
   */
  public String getVerifyClient()
  {
    return _verifyClient;
  }

  /**
   * Sets the key-manager-factory
   */
  public void setKeyManagerFactory(String keyManagerFactory)
  {
    _keyManagerFactory = keyManagerFactory;
  }

  /**
   * Sets the KeyManagerFactory algorithm
   */
  public void setKeyManagerAlgorithm(String keyManagerAlgorithm)
  {
    if (! "".equals(keyManagerAlgorithm)) {
      _keyManagerAlgorithm = keyManagerAlgorithm;
    }
    else {
      _keyManagerAlgorithm = null;
    }
  }

  /**
   * Sets the KeyManagerFactory provider
   */
  public void setKeyManagerProvider(String keyManagerProvider)
  {
    if (! "".equals(keyManagerProvider)) {
      _keyManagerProvider = keyManagerProvider;
    }
    else {
      _keyManagerProvider = null;
    }
  }

  /**
   * Sets the self-signed certificate name
   */
  public void setSelfSignedCertificateName(String name)
  {
    _selfSignedName = name;
  }

  /**
   * Sets the ssl-context
   */
  public void setSSLContext(String sslContext)
  {
    _sslContext = sslContext;
  }

  /**
   * Sets the key-store
   */
  public void setKeyStoreType(String keyStore)
  {
    _keyStoreType = keyStore;
  }

  /**
   * Sets the KeyStore password
   */
  public void setKeyStorePassword(String password)
  {
    if (! "".equals(password)) {
      _keyStorePassword = password;
    }
    else {
      _keyStorePassword = null;
    }
  }

  /**
   * Sets the TrustStore type
   */
  public void setTrustStoreAlgorithm(String value)
  {
    if (! "".equals(value)) {
      _trustStoreAlgorithm = value;
    }
    else {
      _trustStoreAlgorithm = null;
    }
  }

  /**
   * Sets the TrustStore provider
   */
  public void setTrustStoreProvider(String value)
  {
    if (! "".equals(value)) {
      _trustStoreProvider = value;
    }
    else {
      _trustStoreProvider = null;
    }
  }

  /**
   * Sets the TrustStore password
   */
  public void setTrustStorePassword(String value)
  {
    if (! "".equals(value)) {
      _trustStorePassword = value;
    }
    else {
      _trustStorePassword = null;
    }
  }
  
  /**
   * Sets the TrustStore type
   */
  public void setTrustStoreType(String value)
  {
    _trustStoreType = value;
  }
  
  /**
   * Sets the TrustStore store
   */
  public void setTrustStoreFile(Path trustStoreFile)
  {
    _trustStoreFile = trustStoreFile;
  }

  /**
   * Sets the protocol
   */
  public void setProtocol(String protocol)
  {
    _protocols = protocol.split("[\\s,]+");
  }

  public Boolean getHonorCipherOrder()
  {
    return _isHonorCipherOrder;
  }

  public void setHonorCipherOrder(Boolean isHonorCipherOrder)
  {
    if (_honorCipherOrderMethod == null)
      log.log(Level.WARNING, "honor-cipher-order requires JDK 1.8");

    _isHonorCipherOrder = isHonorCipherOrder;
  }
  
  /**
   * Sets the key store instance
   */
  public void setKeyStoreInstance(KeyStore keyStore) {
    _keyStore = keyStore;
  }
  
  /**
   * Sets the trust store instance
   */
  public void setTrustStoreInstance(KeyStore trustStore) {
    _trustStore = trustStore;
  }

  /**
   * Initialize
   */
  @PostConstruct
  public void init()
    throws ConfigException, IOException, GeneralSecurityException
  {
    String keyStorePassword = _keyStorePassword;
    if (keyStorePassword == null) {
      keyStorePassword = _password;
    }
    
    if (_keyStore == null) {
      if (_keyStoreFile != null
          && _password == null
          && _keyStorePassword == null) {
        throw new ConfigException(L.l("'password' or 'key-store-password' is required for JSSE."));
      }
      
      if (_password != null && _keyStoreFile == null)
        throw new ConfigException(L.l("'key-store-file' is required for JSSE."));

      if (_alias != null && _keyStoreFile == null)
        throw new ConfigException(L.l("'alias' requires a key store for JSSE."));

      if (_keyStoreFile == null && _selfSignedName == null)
        throw new ConfigException(L.l("JSSE requires a key-store-file or a self-signed-certificate-name."));

      if (_keyStoreFile == null)
        return;
      
      _keyStore = createKeyStore(_keyStoreType, _keyStoreFile, keyStorePassword);
    }

    if (_alias != null) {      
      String keyPassword = _password;
      if (keyPassword == null) {
        keyPassword = _keyStorePassword;
      }
      
      Key key = _keyStore.getKey(_alias, getPasswordChars(keyPassword));

      if (key == null)
        throw new ConfigException(L.l("JSSE alias '{0}' does not have a corresponding key.",
                                  _alias));

      Certificate []certChain = _keyStore.getCertificateChain(_alias);
      
      if (certChain == null)
        throw new ConfigException(L.l("JSSE alias '{0}' does not have a corresponding certificate chain.",
                                  _alias));

      _keyStore = KeyStore.getInstance(_keyStoreType);
      _keyStore.load(null, getPasswordChars(keyStorePassword));

      _keyStore.setKeyEntry(_alias, key, getPasswordChars(keyPassword), certChain);
    }
  }
  
  private static KeyStore createKeyStore(String keyStoreType,
                                         Path keyStoreFile,
                                         String keyStorePassword)
    throws IOException, GeneralSecurityException
  {
    KeyStore keyStore = KeyStore.getInstance(keyStoreType);
    
    InputStream is = keyStoreFile.openRead();
    try {
      keyStore.load(is, getPasswordChars(keyStorePassword));
    } finally {
      is.close();
    }
    
    return keyStore;
  }
  
  private static char[] getPasswordChars(String password) {
    return password != null ? password.toCharArray() : null;
  }

  /**
   * Creates the SSL ServerSocket.
   */
  public QServerSocket create(InetAddress host, int port)
    throws IOException, GeneralSecurityException
  {
    SSLServerSocketFactory factory = null;
    
    if (_keyStore != null) {
      SSLContext sslContext = SSLContext.getInstance(_sslContext);

      KeyManagerFactory kmf;
      
      if (_keyManagerAlgorithm != null || _keyManagerProvider != null) {
        kmf = KeyManagerFactory.getInstance(_keyManagerAlgorithm,
                                            _keyManagerProvider);
      }
      else {
        kmf = KeyManagerFactory.getInstance(_keyManagerFactory);
      }
      
      String keyPassword = _password;
      if (keyPassword == null) {
        keyPassword = _keyStorePassword;
      }
      
      kmf.init(_keyStore, getPasswordChars(keyPassword));
      
      sslContext.init(kmf.getKeyManagers(), createTrustStore(), null);

      /*
      if (_cipherSuites != null)
        sslContext.createSSLEngine().setEnabledCipherSuites(_cipherSuites);

      if (_protocols != null)
        sslContext.createSSLEngine().setEnabledProtocols(_protocols);
      */

      factory = sslContext.getServerSocketFactory();
    }
    else {
      factory = createAnonymousFactory(host, port);
    }

    ServerSocket serverSocket;

    int listen = 100;

    if (host == null)
      serverSocket = factory.createServerSocket(port, listen);
    else
      serverSocket = factory.createServerSocket(port, listen, host);

    SSLServerSocket sslServerSocket = (SSLServerSocket) serverSocket;

    if (_cipherSuites != null) {
      sslServerSocket.setEnabledCipherSuites(_cipherSuites);
    }
    
    if (_cipherSuitesForbidden != null) {
      String []cipherSuites = sslServerSocket.getEnabledCipherSuites();
      
      if (cipherSuites == null)
        cipherSuites = sslServerSocket.getSupportedCipherSuites();
      
      ArrayList<String> cipherList = new ArrayList<String>();
      
      for (String cipher : cipherSuites) {
        if (! isCipherForbidden(cipher, _cipherSuitesForbidden)) {
          cipherList.add(cipher);
        }
      }
      
      cipherSuites = new String[cipherList.size()];
      cipherList.toArray(cipherSuites);
      
      sslServerSocket.setEnabledCipherSuites(cipherSuites);
    }

    if (_protocols != null) {
      try {
        sslServerSocket.setEnabledProtocols(_protocols);
      } catch (Exception e) {
        throw ConfigException.create(L.l("Invalid protocols '{0}', expected from list '{1}'\n  {2}",
                                  Arrays.asList(_protocols),
                                  Arrays.asList(sslServerSocket.getSupportedProtocols()),
                                  e.toString()),
                              e);
      }
    }
    
    if ("required".equals(_verifyClient))
      sslServerSocket.setNeedClientAuth(true);
    else if ("optional".equals(_verifyClient))
      sslServerSocket.setWantClientAuth(true);

    setHonorCipherOrder(sslServerSocket);

    return new QServerSocketWrapper(serverSocket);
  }
  
  private TrustManager []createTrustStore()
      throws IOException, GeneralSecurityException
  {    
    if (_trustStore == null && _trustStoreFile == null) {
      return null;
    }
    
    KeyStore trustStore = _trustStore;
    
    if (trustStore == null) {
      trustStore
        = createKeyStore(_trustStoreType, _trustStoreFile, _trustStorePassword);
    }
        
    String algorithm = _trustStoreAlgorithm;
    if (algorithm == null) {
      algorithm = TrustManagerFactory.getDefaultAlgorithm();
    }
    
    TrustManagerFactory tmf;
    
    if (_trustStoreProvider != null) {
      tmf = TrustManagerFactory.getInstance(algorithm, _trustStoreProvider);
    }
    else {
      tmf = TrustManagerFactory.getInstance(algorithm);
    }

    tmf.init(trustStore);
    
    return tmf.getTrustManagers();
  }

  private void setHonorCipherOrder(SSLServerSocket serverSocket)
  {
    if (_isHonorCipherOrder == null)
      return;

    if (_honorCipherOrderMethod == null)
      return;

    try {
      SSLParameters params
        = (SSLParameters) _getSSLParametersMethod.invoke(serverSocket);

      _honorCipherOrderMethod.invoke(params, _isHonorCipherOrder);
      
      if (_setSSLParameters != null) {
        _setSSLParameters.invoke(serverSocket, params);
      }
      
      log.log(Level.FINER, L.l("setting honor-cipher-order {0}",
                               _isHonorCipherOrder));
    } catch (Throwable t) {
      log.log(Level.WARNING, t.getMessage(), t);
    }
  }
  
  private boolean isCipherForbidden(String cipher,
                                    String []forbiddenList)
  {
    for (String forbidden : forbiddenList) {
      if (cipher.indexOf(forbidden) >= 0) {
        return true;
      }
    }
    
    return false;
  }

  private SSLServerSocketFactory createAnonymousFactory(InetAddress hostAddr,
                                                        int port)
    throws IOException, GeneralSecurityException
  {
    SSLContext sslContext = SSLContext.getInstance(_sslContext);

    String []cipherSuites = _cipherSuites;

    /*
    if (cipherSuites == null) {
      cipherSuites = sslContext.createSSLEngine().getSupportedCipherSuites();
    }
    */

    String selfSignedName = _selfSignedName;

    if (selfSignedName == null
        || "".equals(selfSignedName)
        || "*".equals(selfSignedName)) {
      if (hostAddr != null)
        selfSignedName = hostAddr.getHostName();
      else {
        InetAddress addr = InetAddress.getLocalHost();

        selfSignedName = addr.getHostAddress();
      }
    }
    
    SelfSignedCert cert = createSelfSignedCert(selfSignedName, cipherSuites);

    if (cert == null)
      throw new ConfigException(L.l("Cannot generate anonymous certificate"));
      
    sslContext.init(cert.getKeyManagers(), null, null);

    // SSLEngine engine = sslContext.createSSLEngine();

    SSLServerSocketFactory factory = sslContext.getServerSocketFactory();

    return factory;
  }
  
  private SelfSignedCert createSelfSignedCert(String name, 
                                              String []cipherSuites)
  {
    Path dataDir = RootDirectorySystem.getCurrentDataDirectory();
    Path certDir = dataDir.lookup("certs");
    
    SelfSignedCert cert = null;
        
    try {
      Path certPath = certDir.lookup(name + ".cert");
            
      if (certPath.canRead()) {
        ReadStream is = certPath.openRead();
        
        try {
          Hessian2Input hIn = new Hessian2Input(is);
          
          cert = (SelfSignedCert) hIn.readObject(SelfSignedCert.class);
          
          hIn.close();

          if (! cert.isExpired())
            return cert;
        } finally {
          IoUtil.close(is);
        }
      }
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);      
    }
      
    cert = SelfSignedCert.create(name, cipherSuites);
        
    try {
      certDir.mkdirs();
      
      Path certPath = certDir.lookup(name + ".cert");
      
      WriteStream os = certPath.openWrite();
        
      try {
        Hessian2Output hOut = new Hessian2Output(os);
        
        hOut.writeObject(cert);
        
        hOut.close();
      } finally {
        IoUtil.close(os);
      }
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);      
    }
        
    return cert;
  }
  
  /**
   * Creates the SSL ServerSocket.
   */
  public QServerSocket bind(QServerSocket ss)
    throws ConfigException, IOException, GeneralSecurityException
  {
    throw new ConfigException(L.l("jsse is not allowed here"));
  }

  static {
    Method setSSLParameters = null;
    
    try {
      Method method = SSLServerSocket.class.getMethod("getSSLParameters");

      method.setAccessible(true);
      _getSSLParametersMethod = method;

      method = SSLParameters.class.getMethod("setUseCipherSuitesOrder",
                                             boolean.class);
      method.setAccessible(true);

      _honorCipherOrderMethod = method;
      
      setSSLParameters = SSLServerSocket.class.getMethod("setSSLParameters", SSLParameters.class);
    } catch (Exception e) {
      log.log(Level.FINER, e.getMessage(), e);
    }
    
    _setSSLParameters = setSSLParameters;
  }
}

