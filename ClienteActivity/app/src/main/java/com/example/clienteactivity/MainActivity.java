package com.example.clienteactivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    static final int PICK_IMAGE_REQUEST = 1;
    static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION = 1;
    static final int SocketServerPORT = 8080;

    LinearLayout loginPanel, chatPanel;
    EditText editTextUserName, editTextAddress;
    Button buttonConnect, buttonDisconnect, buttonSend;
    TextView chatMsg, textPort;
    EditText editTextSay;
    ImageView imagePreview;

    private byte[] imageBytes;
    private ChatClientThread chatClientThread;
    private String msgLog = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Obteniendo las referencias o datos de la interfaz de usuario
        loginPanel = findViewById(R.id.loginpanel);
        chatPanel = findViewById(R.id.chatpanel);
        editTextUserName = findViewById(R.id.username);
        editTextAddress = findViewById(R.id.address);
        textPort = findViewById(R.id.port);
        textPort.setText("Puerto Asignado: " + SocketServerPORT);
        buttonConnect = findViewById(R.id.connect);
        buttonConnect.setOnClickListener(buttonConnectOnClickListener);
        buttonDisconnect = findViewById(R.id.disconnect);
        buttonDisconnect.setOnClickListener(buttonDisconnectOnClickListener);
        chatMsg = findViewById(R.id.chatmsg);
        imagePreview = findViewById(R.id.imagePreview);
        editTextSay = findViewById(R.id.say);
        buttonSend = findViewById(R.id.send);
        buttonSend.setOnClickListener(buttonSendOnClickListener);
        Button buttonSelectImage = findViewById(R.id.selectImage);


        buttonSelectImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    selectImage();
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_EXTERNAL_STORAGE_PERMISSION);
                }
            }
        });

        // Esto es Necesario para evitar NetworkOnMainThreadException,  (solo para fines educativos)
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    // Método para seleccionar una imagen de la galería de nuestro dispositivo movil android
    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    //Botón de desconexión
    View.OnClickListener buttonDisconnectOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (chatClientThread == null) {
                return;
            }
            chatClientThread.disconnect();
        }
    };

    //Botón de enviar
    View.OnClickListener buttonSendOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (editTextSay.getText().toString().equals("")) {
                return;
            }

            if (imageBytes != null) {
                // Si hay una imagen cargada, se enviara al servidor
                chatClientThread.sendImage(imageBytes);
                imageBytes = null;

                runOnUiThread(() -> {
                    imagePreview.setImageResource(0);
                    Toast.makeText(MainActivity.this, "Imagen Enviada", Toast.LENGTH_SHORT).show();//msj que nos indica que se a enviado la imagen
                });
            }

            // En este codigo se envia el mensaje de texto escrito por el cliente o los clientes.
            String msg = editTextSay.getText().toString();
            chatClientThread.sendMsg(msg);
            editTextSay.setText("");
        }
    };


    View.OnClickListener buttonConnectOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String textUserName = editTextUserName.getText().toString();
            if (textUserName.isEmpty()) {
                Toast.makeText(MainActivity.this, "Introduzca su Nombre", Toast.LENGTH_LONG).show();
                return;
            }

            String textAddress = editTextAddress.getText().toString();
            if (textAddress.isEmpty()) {
                Toast.makeText(MainActivity.this, "Ingrese Su Dirección", Toast.LENGTH_LONG).show();
                return;
            }

            msgLog = "";
            chatMsg.setText(msgLog);
            loginPanel.setVisibility(View.GONE);
            chatPanel.setVisibility(View.VISIBLE);

            // Proceso de Iniciar el hilo del cliente al chat
            chatClientThread = new ChatClientThread(textUserName, textAddress, SocketServerPORT);
            chatClientThread.start();
        }
    };

    // Esta clase nos ayuda a realizar la carga asíncrona de imágenes
    private class SendImageTask extends AsyncTask<Uri, Void, Void> {
        @Override
        protected Void doInBackground(Uri... uris) {
            Uri uri = uris[0];
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                imageBytes = byteArrayOutputStream.toByteArray();

                runOnUiThread(() -> {
                    imagePreview.setImageBitmap(bitmap);
                });
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show();
            }
            return null;
        }
    }

    // Clase para manejar la lógica del cliente de chat
    private class ChatClientThread extends Thread {
        String name;
        String dstAddress;
        int dstPort;
        String msgToSend = "";
        boolean goOut = false;
        Socket socket;
        DataOutputStream dataOutputStream;
        DataInputStream dataInputStream;

        ChatClientThread(String name, String address, int port) {
            this.name = name;
            dstAddress = address;
            dstPort = port;
        }

        // Método para enviar una imagen al servidor
        private void sendImage(byte[] image) {
            try {
                dataOutputStream.writeUTF("IMAGE");
                dataOutputStream.writeInt(image.length);
                dataOutputStream.write(image, 0, image.length);
                dataOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Método para enviar un mensaje de texto al servidor
        private void sendMsg(String msg) {
            msgToSend = msg;
        }

        // Método para desconectar el cliente
        private void disconnect() {
            goOut = true;
        }

        //metodo que establece una conexion con una dirrecion ip y un puerto especifico
        @Override
        public void run() {
            try {
                socket = new Socket(dstAddress, dstPort);

                //Objetos para enviar y recibir datos a través del socket
                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                dataInputStream = new DataInputStream(socket.getInputStream());

                //asegurando que se envien los datos al servidor con el metodo flush
                dataOutputStream.writeUTF(name);
                dataOutputStream.flush();

                //bucle que se ejecutara siempre hasta q la variable sea falsa,  siempre esta leyendo los msj
                while (!goOut) {

                    //siempre verifica si hay datos disponibles que deba recibir el servidor, si los hay los lee y actualiza la interfaz
                    if (dataInputStream.available() > 0) {
                        String msg = dataInputStream.readUTF();
                        msgLog += msg;

                        MainActivity.this.runOnUiThread(() -> {
                            chatMsg.setText(msgLog);
                        });
                    }

                    if (!msgToSend.equals("")) {
                        dataOutputStream.writeUTF(msgToSend);
                        dataOutputStream.flush();
                        msgToSend = "";
                    }
                }

            } catch (UnknownHostException e) { //manejando las excepciones durante la creacion del socket o la comunicacion con el servidor
                e.printStackTrace();
                final String eString = e.toString();
                MainActivity.this.runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, eString, Toast.LENGTH_LONG).show();
                });
            } catch (IOException e) {
                e.printStackTrace();
                final String eString = e.toString();
                MainActivity.this.runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, eString, Toast.LENGTH_LONG).show();
                });
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (dataOutputStream != null) {
                    try {
                        dataOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (dataInputStream != null) {
                    try {
                        dataInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                MainActivity.this.runOnUiThread(() -> {
                    loginPanel.setVisibility(View.VISIBLE);
                    chatPanel.setVisibility(View.GONE);
                });
            }
        }
    }

    // Método para manejar los resultados de la solicitud de permisos de la imagen
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_READ_EXTERNAL_STORAGE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show();
                } else {

                    selectImage();
                }
                break;
            default:
                break;
        }
    }

    // Método para manejar los resultados de la actividad de selección de imagen
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();

            // Ejecutar la tarea asíncrona para procesar la imagen seleccionada
            new SendImageTask().execute(uri);
        }
    }
}
