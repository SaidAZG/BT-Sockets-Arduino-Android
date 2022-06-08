package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import com.example.myapplication.databinding.ActivityMainBinding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {


    private ActivityMainBinding binding;
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    public static String address = null;
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public boolean activar;
    private boolean first = true;
    private float temperature;
    private BluetoothDevice device;
    public static Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);//Ignorar el modo oscuro
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        verificarBluetooth();

        binding.conectar.setOnClickListener(view -> {
            conectar();
        });

        /*
        binding.sensores.setOnClickListener(view -> {
            comunicar();
        });*/

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 2) {
                    String arduinoMsg = msg.obj.toString(); // Read message from Arduino
                    String[] valores = arduinoMsg.split("&");
                    binding.BTDInput.setText("Temperatura: "+valores[0]+" °C");
                    binding.temperature.setText(valores[0]+" °C");

                    if (!first){
                        float current = Float.parseFloat(valores[0]);
                        float diference = temperature - current;
                        //Log.d("Data","Diferencia "+temperature+" - "+current+" = "+diference);
                        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(binding.temperature,"translationY", diference*70f);
                        objectAnimator.setDuration(400);
                        objectAnimator.start();
                    }else{
                        temperature = Float.parseFloat(valores[0]);
                        first = false;
                    }

                    /*if (valores.length > 3) {
                        for (int i = 0; i < valores.length; i++)
                            Log.d("BT Input Values" + i, valores[i]);
                        binding.BTDInput.setText("Humedad: " + valores[0] + "g/m3 Temperatura: " + valores[1] + "°C\n");
                        binding.BTDInput.append("Voltaje: " + valores[2] + " volts\n");
                        binding.BTDInput.append(valores[3] + "\n");
                        binding.BTDInput.append("RPM: " + valores[4]);
                    }*/
                }
            }
        };
    }


    private void comunicar() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }else{
            //Creacion del socket
            try {
                btSocket = device.createInsecureRfcommSocketToServiceRecord(device.getUuids()[0].getUuid());
                Log.d("BT Socket","Socket creado\nDispositivo:"+device.getName()+"\nUUID:"+device.getUuids()[0].getUuid());
            } catch (IOException e) {
                Toast.makeText(getBaseContext(), "La creacci贸n del Socket fallo", Toast.LENGTH_LONG).show();
            }
            // Establece la conexi贸n con el socket Bluetooth.
            Log.d("BT Socket Status","Intentando conectar socket");
            try {
                btSocket.connect();
            } catch (IOException e) {
                Log.d("BT Socket Status","No fue posible conectar el socket... "+e);
            }
            Log.d("BT Socket Status","Conected: "+btSocket.isConnected());
            //Iniciar el hilo de conexion
            ConnectedThread myConexionBT = new ConnectedThread(btSocket);
            Log.d("BT Connect","Hilo de conexion creado");

            myConexionBT.start();
        }
    }

    private void verificarBluetooth() {
        if (btAdapter.isEnabled()) {
            Log.d("BT Adapter", "BT Adapter habilitado");
            activar = true;
        } else {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.d("BT Permission","Permiso no otorgado");
            } else {
                startActivityForResult(intent, 1);
            }
        }

    }

    public void conectar() {
        Log.d("BT Proccess","Iniciando proceso de conexion");
        if (activar) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.d("BT Permission","Permiso no otorgado 1");
                return;
            }
            //Obtener la direccion del arduino
            Set<BluetoothDevice> pairedDeveicesList = btAdapter.getBondedDevices();
            for (BluetoothDevice pairedDevice : pairedDeveicesList) {
                Log.d("BT Devices",pairedDevice.getName());
                Log.d("BT Devices Address",pairedDevice.getAddress());
                if (pairedDevice.getName().equals("HC-06")) {
                    address = pairedDevice.getAddress();
                    binding.BTDName.setText("Nombre: " + pairedDevice.getName());
                    binding.BTDAddress.setText("Dirección: " + address);
                    //binding.sensores.setEnabled(true);
                }
            }

            //Toast.makeText(this,"Dispositivos encontrados: "+pairedDeveicesList.size(), Toast.LENGTH_LONG).show();
            //Iniciar el adaptador
            device = btAdapter.getRemoteDevice(address);
            comunicar();
        }
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {

            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            BufferedReader reader = new BufferedReader(new InputStreamReader(mmInStream));
            String res;
            Log.d("BT Connect","Hilo de conexion iniciado");
            // Se mantiene en modo escucha para determinar el ingreso de datos
            while (true) {
                try {
                    res = reader.readLine();
                    Message mensaje = new Message();
                    mensaje.what = 2;
                    mensaje.obj = res;
                    handler.sendMessage(mensaje);
                    Log.d("BT Connect",res);
                } catch (IOException e) {
                    Log.d("BT ","desconectado");
                    //TODO de momento aqui es donde esta cayendo el programa porque el socket no esta abierto
                    break;
                }
            }
        }

        //Envio de trama
        public void write(String input) {
            try {
                mmOutStream.write(input.getBytes());
            } catch (IOException e) {
                //si no es posible enviar datos se cierra la conexi贸n
                Toast.makeText(getBaseContext(), "La Conexion fallo", Toast.LENGTH_LONG).show();
                finish();
            }
        }

    }
}