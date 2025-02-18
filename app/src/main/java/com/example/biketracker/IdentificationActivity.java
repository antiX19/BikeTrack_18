package com.example.biketracker;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;


import com.example.bike_tracker.R;

import java.util.ArrayList;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class IdentificationActivity extends AppCompatActivity {

    private EditText editTextEmail, editTextPseudo, editTextPassword, editTextConfirmPassword;
    private Button buttonTrouveTonVelo, buttonConnecter;
    private ListView listViewBleDevices;
    private ArrayAdapter<String> deviceAdapter;
    private ArrayList<String> deviceNames;
    private ArrayList<BluetoothDevice> foundDevices;
    String email;
    String pseudo;
    String password;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanCallback scanCallback;
    private Handler handler = new Handler();
    private static final long SCAN_PERIOD = 10000;

    // Variables pour gérer la connexion BLE en cours
    private static BluetoothGatt currentGatt = null;
    private BluetoothDevice currentConnectedDevice = null;


    // Remplacez ces UUID par ceux correspondant à votre module BLE
    private static final UUID SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_identification);

        // Liaison des vues
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPseudo = findViewById(R.id.editTextPseudo);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        buttonTrouveTonVelo = findViewById(R.id.buttonTrouveTonVelo);
        buttonConnecter = findViewById(R.id.buttonInscription);
        listViewBleDevices = findViewById(R.id.listViewBleDevices);

        // Désactivation du bouton "Connecter" tant que les champs ne sont pas valides
        buttonConnecter.setEnabled(false);

        // Initialisation des listes pour le scan BLE
        deviceNames = new ArrayList<>();
        foundDevices = new ArrayList<>();
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
        listViewBleDevices.setAdapter(deviceAdapter);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://13.36.126.63/") // URL de base du serveur
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        ApiService apiService = retrofit.create(ApiService.class);


        buttonConnecter.setOnClickListener(v -> {
            // (Après avoir éventuellement validé les champs et envoyé les données à votre API)
            // Vérifiez que la connexion BLE est bien établie :
            if (currentGatt == null) {
                Toast.makeText(IdentificationActivity.this, "Aucun module BLE connecté", Toast.LENGTH_SHORT).show();
                return;
            }
            // Passe à l'activité de renommage
            Intent intent = new Intent(IdentificationActivity.this, RenameActivity.class);
            startActivity(intent);
            // Initialiser Retrofit
            // Créer un objet VeloData à envoyer
            UsersData usersData = new UsersData(pseudo,email,password);

            // Envoyer les données via POST
            Call<UsersData> call = apiService.postUsersData(usersData);
            call.enqueue(new Callback<UsersData>() {
                @Override
                public void onResponse(Call<UsersData> call, Response<UsersData> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(IdentificationActivity.this, "Données envoyées avec succès", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(IdentificationActivity.this, "Erreur côté serveur : " + response.message(), Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(Call<UsersData> call, Throwable t) {
                    Toast.makeText(IdentificationActivity.this, "Erreur de connexion : " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("IdentificationActivity", "Erreur de connexion", t);
                }
            });

        });
        // Initialisation du Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth non supporté sur cet appareil", Toast.LENGTH_SHORT).show();
            finish();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        // Ajout d'un TextWatcher pour valider en temps réel l'email et les mots de passe
        TextWatcher inputWatcher = new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateInputs();
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
        };
        editTextEmail.addTextChangedListener(inputWatcher);
        editTextPassword.addTextChangedListener(inputWatcher);
        editTextConfirmPassword.addTextChangedListener(inputWatcher);

        // Bouton pour lancer le scan BLE
        buttonTrouveTonVelo.setOnClickListener(v -> startBleScan());

        // Bouton "Connecter" (vous pouvez y ajouter la logique finale de connexion si besoin)

        // Gestion du clic sur un élément de la ListView (module BLE)
        listViewBleDevices.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = foundDevices.get(position);
            // Si le module est déjà connecté, on ne fait rien
            if (currentConnectedDevice != null && currentConnectedDevice.getAddress().equals(device.getAddress())) {
                Toast.makeText(IdentificationActivity.this, "Module déjà connecté", Toast.LENGTH_SHORT).show();
            } else {
                // Si un autre module est connecté, on peut choisir de ne pas forcer la déconnexion
                // Ici, on se connecte uniquement si aucun module n'est connecté
                if (currentConnectedDevice == null) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    currentGatt = device.connectGatt(IdentificationActivity.this, false, bluetoothGattCallback);
                    currentConnectedDevice = device;
                    Toast.makeText(IdentificationActivity.this, "Tentative de connexion au module...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(IdentificationActivity.this, "Un module est déjà connecté", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }
    public static BluetoothGatt getCurrentGatt() {
        return currentGatt;
    }
    /**
     * Valide l'email et les mots de passe.
     * Affiche un message d'erreur sur le champ concerné si invalide.
     * Active le bouton "Connecter" uniquement si toutes les validations sont correctes.
     */
    private void validateInputs() {
        // Vérification de l'email
        email = editTextEmail.getText().toString().trim();
        boolean emailValid = !email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches();
        if (!emailValid) {
            editTextEmail.setError("Adresse email invalide");
        } else {
            editTextEmail.setError(null);
        }
        pseudo = editTextPseudo.getText().toString().trim();
        // Vérification des mots de passe
        password = editTextPassword.getText().toString();
        String confirmPassword = editTextConfirmPassword.getText().toString();
        boolean passwordsValid = !password.isEmpty() && password.equals(confirmPassword);
        if (!passwordsValid) {
            editTextPassword.setError("Les mots de passe ne correspondent pas");
            editTextConfirmPassword.setError("Les mots de passe ne correspondent pas");
        } else {
            editTextPassword.setError(null);
            editTextConfirmPassword.setError(null);
        }

        // Activation du bouton "Connecter" si les entrées sont valides
        buttonConnecter.setEnabled(emailValid && passwordsValid);
    }

    /**
     * Lance le scan BLE et affiche dans la ListView uniquement les modules dont le nom commence par "HM".
     */
    private void startBleScan() {
        // Vérification de la permission ACCESS_FINE_LOCATION
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        // Réinitialisation des listes
        foundDevices.clear();
        deviceNames.clear();
        deviceAdapter.notifyDataSetChanged();

        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "Scanner BLE non disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        // Définition du callback du scan BLE
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                if (device.getName() != null && device.getName().startsWith("HM")) {
                    if (!foundDevices.contains(device)) {
                        foundDevices.add(device);
                        // Par défaut, afficher " - déconnecté"
                        deviceNames.add(device.getName() + " - déconnecté");
                        runOnUiThread(() -> deviceAdapter.notifyDataSetChanged());
                    }
                }
            }
            @Override
            public void onBatchScanResults(java.util.List<ScanResult> results) {
                for (ScanResult result : results) {
                    BluetoothDevice device = result.getDevice();
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    if (device.getName() != null && device.getName().startsWith("HM")) {
                        if (!foundDevices.contains(device)) {
                            foundDevices.add(device);
                            deviceNames.add(device.getName() + " - déconnecté");
                        }
                    }
                }
                runOnUiThread(() -> deviceAdapter.notifyDataSetChanged());
            }
            @Override
            public void onScanFailed(int errorCode) {
                runOnUiThread(() ->
                        Toast.makeText(IdentificationActivity.this, "Scan échoué: " + errorCode, Toast.LENGTH_SHORT).show());
            }
        };

        // Démarrer le scan BLE
        bluetoothLeScanner.startScan(scanCallback);
        Toast.makeText(this, "Scanning des dispositifs BLE...", Toast.LENGTH_SHORT).show();

        // Arrêter le scan après SCAN_PERIOD millisecondes
        handler.postDelayed(() -> {
            bluetoothLeScanner.stopScan(scanCallback);
            runOnUiThread(() -> Toast.makeText(IdentificationActivity.this, "Scan terminé", Toast.LENGTH_SHORT).show());
        }, SCAN_PERIOD);
    }

    /**
     * Callback pour gérer les changements d'état de connexion BLE.
     * Une fois réellement connecté, on met à jour l'affichage.
     */
    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> {
                    Toast.makeText(IdentificationActivity.this, "Module connecté", Toast.LENGTH_SHORT).show();
                    updateDeviceStatus(gatt.getDevice(), true);
                });
                // Lancez la découverte des services dès que la connexion est établie
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                gatt.discoverServices();
            } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(() -> {
                    Toast.makeText(IdentificationActivity.this, "Module déconnecté", Toast.LENGTH_SHORT).show();
                    updateDeviceStatus(gatt.getDevice(), false);
                });
                if(currentConnectedDevice != null &&
                        currentConnectedDevice.getAddress().equals(gatt.getDevice().getAddress())) {
                    currentGatt = null;
                    currentConnectedDevice = null;
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread(() ->
                        Toast.makeText(IdentificationActivity.this, "Services découverts", Toast.LENGTH_SHORT).show()
                );
                // À ce stade, le service devrait être disponible :
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service == null) {
                    runOnUiThread(() ->
                            Toast.makeText(IdentificationActivity.this, "Service AT non trouvé", Toast.LENGTH_SHORT).show()
                    );
                }
            } else {
                runOnUiThread(() ->
                        Toast.makeText(IdentificationActivity.this, "Échec de la découverte des services", Toast.LENGTH_SHORT).show()
                );
            }
        }
    };


    /**
     * Met à jour l'affichage du statut du module dans la ListView.
     * @param device Le module concerné.
     * @param connected true si le module est connecté, false sinon.
     */
    private void updateDeviceStatus(BluetoothDevice device, boolean connected) {
        int index = foundDevices.indexOf(device);
        if (index != -1) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            String baseName = device.getName();
            if (connected) {
                deviceNames.set(index, baseName + " - connecté");
            } else {
                deviceNames.set(index, baseName + " - déconnecté");
            }
            runOnUiThread(() -> deviceAdapter.notifyDataSetChanged());
        }
    }

    public static void sendAtCommand(String command) {
        if (currentGatt == null) {
            //Toast.makeText(this, "Pas de connexion GATT active", Toast.LENGTH_SHORT).show();
            return;
        }
        BluetoothGattService service = currentGatt.getService(SERVICE_UUID);
        //Toast.makeText(this, "Service AT non trouvé", Toast.LENGTH_SHORT).show();

        if (service == null) {
            //Toast.makeText(this, "Service AT non trouvé", Toast.LENGTH_SHORT).show();
            return;
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
        if (characteristic == null) {
           // Toast.makeText(this, "Caractéristique AT non trouvée", Toast.LENGTH_SHORT).show();
            return;
        }
        characteristic.setValue(command.getBytes());
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        currentGatt.writeCharacteristic(characteristic);
    }
}
