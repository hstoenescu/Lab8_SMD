package laborator03.softwareup.com.lab3_ex1;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class MainActivity extends AppCompatActivity {

    // check network connectivity using CM
    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo;
    }

    /**
     * Converts the contents of an InputStream to a String.
     */
    public String readStream(InputStream stream, int maxReadSize)
            throws IOException, UnsupportedEncodingException {
        Reader reader = null;
        reader = new InputStreamReader(stream, "UTF-8");
        char[] rawBuffer = new char[maxReadSize];
        int readSize;
        StringBuffer buffer = new StringBuffer();
        while (((readSize = reader.read(rawBuffer)) != -1) && maxReadSize > 0) {
            if (readSize > maxReadSize) {
                readSize = maxReadSize;
            }
            buffer.append(rawBuffer, 0, readSize);
            maxReadSize -= readSize;
        }
        return buffer.toString();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // text View - where the page is shown
        final TextView textView = (TextView) findViewById(R.id.textView);

        // show trust store button and if pressed show cert info
        final Button buttonTS = (Button) findViewById(R.id.button_ts);
        buttonTS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TrustManagerFactory trustManagerFactory = null;
                try {
                    trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                try {
                    trustManagerFactory.init((KeyStore) null);
                } catch (KeyStoreException e) {
                    e.printStackTrace();
                }
                X509TrustManager x509TrustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
                int index = 1;
                for (X509Certificate certificate : x509TrustManager.getAcceptedIssuers()) {
                    String cert_str = "Subject:" + certificate.getSubjectDN().getName() + "\nIssuer:" + certificate.getIssuerDN().getName();
                    Log.d("CERT NO." + index, cert_str);
                    index++;
                    //textView.setText(cert_str);
                }
            }
        });

        // edit Text - where the page is added
        final EditText editText = (EditText) findViewById(R.id.editText);


        // define button
        final Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){

                AsyncTask<String, Void, String> task = new AsyncTask<String, Void, String>(){

                    @Override
                    protected void onPreExecute() {
                        NetworkInfo networkInfo = getActiveNetworkInfo();
                        if (networkInfo == null || !networkInfo.isConnected() ||
                                (networkInfo.getType() != ConnectivityManager.TYPE_WIFI
                                        && networkInfo.getType() != ConnectivityManager.TYPE_MOBILE)) {
                            // cancel the task here
                            Log.e("NO_CONN","Failed to find the connectivity to Internet");
                            cancel(true);
                        }
                    }

                    // perform HTTPSUrlConnection task
                    @Override
                    protected String doInBackground(String... params) {


                        // load the CA cert
                        CertificateFactory cf = null;
                        try {
                            cf = CertificateFactory.getInstance("X.509");
                        } catch (CertificateException e) {
                            e.printStackTrace();
                        }
                        Certificate ca = null;
                        InputStream inputStreamCert = getResources().openRawResource(R.raw.badsslcom);
                        try {
                            ca = cf.generateCertificate(inputStreamCert);
                            Log.d("X509CERT","ca=" + ((X509Certificate) ca).getSubjectDN());
                        } catch (CertificateException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                inputStreamCert.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        // Create the key store using the CA
                        String keyStoreType = KeyStore.getDefaultType();
                        KeyStore keyStore = null;
                        try {
                            keyStore = KeyStore.getInstance(keyStoreType);
                        } catch (KeyStoreException e) {
                            e.printStackTrace();
                        }
                        try {
                            keyStore.load(null, null);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        } catch (CertificateException e) {
                            e.printStackTrace();
                        }

                        try {
                            keyStore.setCertificateEntry("ca", ca);
                        } catch (KeyStoreException e) {
                            e.printStackTrace();
                        }

                        // create a trust manager that trusts our CA in keystore
                        String tmfAlg = TrustManagerFactory.getDefaultAlgorithm();
                        TrustManagerFactory tmf = null;
                        try {
                            tmf = TrustManagerFactory.getInstance(tmfAlg);
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }
                        try {
                            tmf.init(keyStore);
                        } catch (KeyStoreException e) {
                            e.printStackTrace();
                        }

                        // create the SSLContext that uses our tmf
                        SSLContext sslContext = null;
                        try {
                            sslContext = SSLContext.getInstance("TLS");
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }
                        try {
                            sslContext.init(null,tmf.getTrustManagers(),null);
                        } catch (KeyManagementException e) {
                            e.printStackTrace();
                        }

                        // open the connection - use the url from the edittext
                        // get the url
                        URL url = null;
                        try {
                            url = new URL(params[0]);
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }

                        InputStream stream = null;
                        HttpsURLConnection httpsURLConnection = null;
                        String result = null;
                        try {
                            httpsURLConnection = (HttpsURLConnection) url.openConnection();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        httpsURLConnection.setSSLSocketFactory(sslContext.getSocketFactory());
                        try {
                            stream = httpsURLConnection.getInputStream();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (stream != null) {
                            try {
                                result = readStream(stream, 5000);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        return result;

                        // ex1 stuff
//                        try {
//                            connection = (HttpsURLConnection) url.openConnection();
//                            connection.setReadTimeout(3000);
//                            connection.setConnectTimeout(3000);
//                            connection.setRequestMethod("GET");
//                            connection.setDoInput(true);
//                            // open the connection
//                            connection.connect();
//                            //Toast.makeText(getApplicationContext(),"CONNECT OK",Toast.LENGTH_LONG).show();
//                            Log.d("CONNECT_OK","The connection is ok");
//
//                            // verify the connection code
//                            int responseCode = connection.getResponseCode();
//                            if (responseCode != HttpsURLConnection.HTTP_OK){
//                                throw  new IOException("HTTP ERROR CODE IS:" + responseCode);
//                            }
//
//                            // get the content of the response
//                            stream = connection.getInputStream();
//                            //Toast.makeText(getApplicationContext(),"GET INPUT STREAM SUCCESS",Toast.LENGTH_LONG).show();
//                            Log.d("INPUT_STREAM_OK","GetInputStream success!");
//                            if (stream != null) {
//                                result = readStream(stream,5000);
//                            }
//
//                            return result;
//
//                        } catch (UnsupportedEncodingException e) {
//                            e.printStackTrace();
//                        } catch (ProtocolException e) {
//                            e.printStackTrace();
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        } finally {
//                            if (stream != null) {
//                                try {
//                                    stream.close();
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
//                            }
//
//                            if (connection != null) {
//                                connection.disconnect();
//                            }
//
//                        }

                        // if not ok, return null string

                    }

                    // executed in the main thd (UI thread)
                    @Override
                    protected void onPostExecute(String content) {
                        textView.setText(content);
                    }
                };

                task.execute(editText.getText().toString());
            }
        });
    }
}
