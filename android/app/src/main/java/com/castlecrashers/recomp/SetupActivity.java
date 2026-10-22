package com.castlecrashers.recomp;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SetupActivity extends Activity {
    private static final String PREFS = "castle_boot_options";
    private static final int REQUEST_DRIVER_FOLDER = 41;
    private static final String TURNIP_DIR = "turnip-driver";
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private Spinner driverModeSpinner;
    private Spinner gpuSpinner;
    private Spinner resolutionSpinner;
    private Spinner presentSpinner;
    private Spinner framesSpinner;
    private TextView driverStatus;
    private ProgressDialog progress;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        CastleApplication.log("SETUP_SCREEN_CREATE");
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        int pad = dp(22);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(pad, dp(30), pad, dp(24));
        page.setBackgroundColor(Color.rgb(13, 18, 24));

        TextView title = new TextView(this);
        title.setText("CASTLE CRASHERS");
        title.setTextColor(Color.rgb(247, 190, 79));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        page.addView(title);

        TextView intro = new TextView(this);
        intro.setText("Inicialização gráfica · Snapdragon 865 / Adreno 650");
        intro.setTextColor(Color.rgb(203, 211, 220));
        intro.setTextSize(14);
        intro.setPadding(0, dp(7), 0, dp(8));
        page.addView(intro);

        TextView api = new TextView(this);
        api.setText("API: Vulkan. O runtime ARM64 deste APK usa Vulkan para emular a GPU do Xbox 360. Turnip é um driver Vulkan opcional para Adreno.");
        api.setTextColor(Color.rgb(234, 238, 242));
        api.setTextSize(14);
        api.setPadding(dp(14), dp(11), dp(14), dp(11));
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(Color.rgb(29, 39, 49));
        panel.setCornerRadius(dp(10));
        api.setBackground(panel);
        page.addView(api, new LinearLayout.LayoutParams(-1, -2));

        driverModeSpinner = addOption(page, "Driver Vulkan", new String[] {
                "Driver do sistema (padrão Samsung)", "Turnip personalizado (pasta selecionada)"
        }, "Turnip só carrega se o driver ARM64 compatível estiver instalado na pasta privada do app.");
        Button chooseDriver = new Button(this);
        chooseDriver.setText("SELECIONAR PASTA TURNIP EM DOWNLOADS");
        page.addView(chooseDriver, new LinearLayout.LayoutParams(-1, dp(50)));
        chooseDriver.setOnClickListener(v -> chooseTurnipFolder());
        driverStatus = new TextView(this);
        TextView driverHelp = new TextView(this);
        driverHelp.setText("Extraia o pacote Turnip e selecione sua pasta (não o ZIP). O app procura libvulkan_freedreno.so e copia a pasta para o armazenamento privado, exigido pelo Android.");
        driverHelp.setTextColor(Color.rgb(173, 184, 195));
        driverHelp.setTextSize(12);
        driverHelp.setPadding(0, dp(4), 0, dp(3));
        page.addView(driverHelp);
        driverStatus.setTextColor(Color.rgb(173, 184, 195));
        driverStatus.setTextSize(12);
        driverStatus.setPadding(0, dp(5), 0, dp(4));
        page.addView(driverStatus);

        gpuSpinner = addOption(page, "Dispositivo Vulkan", new String[] {
                "Automático (recomendado)", "GPU 0 — Adreno 650"
        }, "Usado com o driver do sistema; o Turnip selecionado controla seu próprio dispositivo.");
        resolutionSpinner = addOption(page, "Resolução interna", new String[] {
                "720p — compatibilidade", "1080p — qualidade", "Padrão do sistema"
        }, "720p reduz a carga gráfica.");
        presentSpinner = addOption(page, "Apresentação de quadros", new String[] {
                "FIFO / V-Sync — compatibilidade", "Automático — MAILBOX quando disponível"
        }, "FIFO é o modo de apresentação garantido pelo Vulkan.");
        framesSpinner = addOption(page, "Quadros em voo", new String[] {
                "1 — mais conservador", "2 — padrão"
        }, "Limita quantos quadros ficam pendentes no driver.");

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        driverModeSpinner.setSelection(prefs.getInt("driverMode", 0));
        gpuSpinner.setSelection(prefs.getInt("gpu", 0));
        resolutionSpinner.setSelection(prefs.getInt("resolution", 0));
        presentSpinner.setSelection(prefs.getInt("present", 0));
        framesSpinner.setSelection(prefs.getInt("frames", 0));
        updateDriverStatus();

        Button launch = new Button(this);
        launch.setText("SALVAR E INICIAR JOGO");
        launch.setTextSize(16);
        launch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        launch.setTextColor(Color.rgb(22, 24, 27));
        launch.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(247, 190, 79)));
        LinearLayout.LayoutParams launchParams = new LinearLayout.LayoutParams(-1, dp(56));
        launchParams.topMargin = dp(20);
        page.addView(launch, launchParams);
        launch.setOnClickListener(v -> launchGame(false));

        Button safe = new Button(this);
        safe.setText("INICIAR MODO SEGURO COM DRIVER DO SISTEMA");
        safe.setTextSize(12);
        LinearLayout.LayoutParams safeParams = new LinearLayout.LayoutParams(-1, dp(52));
        safeParams.topMargin = dp(8);
        page.addView(safe, safeParams);
        safe.setOnClickListener(v -> launchGame(true));

        TextView footer = new TextView(this);
        footer.setText("Os registros ficam em Android/data/com.castlecrashers.recomp/files/logs/");
        footer.setTextColor(Color.rgb(157, 171, 184));
        footer.setTextSize(12);
        footer.setPadding(0, dp(14), 0, 0);
        page.addView(footer);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        setContentView(scroll);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_DRIVER_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree, data.getFlags() &
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        } catch (SecurityException ignored) {
            CastleApplication.log("SAF did not grant persistent permission; copying while current grant is valid");
        }
        progress = ProgressDialog.show(this, "Driver Turnip", "Copiando os arquivos para armazenamento privado…", true, false);
        ioExecutor.execute(() -> {
            String error = null;
            String foundName = null;
            try {
                File destination = new File(getFilesDir(), TURNIP_DIR);
                deleteTree(destination);
                if (!destination.mkdirs() && !destination.isDirectory()) throw new IOException("Não foi possível criar " + destination);
                String rootDocumentId = DocumentsContract.getTreeDocumentId(tree);
                copyDocumentTree(tree, rootDocumentId, destination, destination, 0);
                File driver = findDriver(destination);
                if (driver == null) throw new IOException("Não encontrei libvulkan_freedreno.so na pasta. Extraia o pacote Turnip e selecione a pasta que contém a biblioteca .so e os arquivos auxiliares.");
                foundName = driver.getName();
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString("turnipDir", driver.getParentFile().getAbsolutePath())
                        .putString("turnipName", foundName)
                        .putInt("driverMode", 1).apply();
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String finalError = error;
            final String finalName = foundName;
            runOnUiThread(() -> {
                if (progress != null && progress.isShowing()) progress.dismiss();
                if (finalError != null) {
                    CastleApplication.log("TURNIP_IMPORT_FAILED: " + finalError);
                    Toast.makeText(this, finalError, Toast.LENGTH_LONG).show();
                    updateDriverStatus();
                    return;
                }
                driverModeSpinner.setSelection(1);
                updateDriverStatus();
                CastleApplication.log("TURNIP_IMPORTED: " + finalName);
                Toast.makeText(this, "Driver importado: " + finalName, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void chooseTurnipFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_DRIVER_FOLDER);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir o seletor de pastas: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void copyDocumentTree(Uri tree, String documentId, File current, File root, int depth) throws IOException {
        if (depth > 12) throw new IOException("A pasta Turnip tem níveis demais");
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = getContentResolver().query(children, columns, null, null, null)) {
            if (cursor == null) throw new IOException("Não foi possível ler a pasta escolhida");
            while (cursor.moveToNext()) {
                String childId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if (name == null || name.isEmpty() || name.contains("/") || name.equals(".") || name.equals("..")) continue;
                File output = new File(current, name);
                if (!output.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) {
                    throw new IOException("Nome de arquivo inválido no driver");
                }
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (!output.isDirectory() && !output.mkdirs()) throw new IOException("Falha ao criar " + name);
                    copyDocumentTree(tree, childId, output, root, depth + 1);
                } else {
                    Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, childId);
                    try (InputStream input = getContentResolver().openInputStream(document);
                         FileOutputStream outputStream = new FileOutputStream(output)) {
                        if (input == null) throw new IOException("Não foi possível abrir " + name);
                        byte[] buffer = new byte[256 * 1024];
                        int count;
                        while ((count = input.read(buffer)) != -1) outputStream.write(buffer, 0, count);
                        outputStream.getFD().sync();
                    }
                }
            }
        } catch (SecurityException e) {
            throw new IOException("Acesso negado à pasta do driver", e);
        }
    }

    private static File findDriver(File root) {
        File[] files = root.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isDirectory()) {
                File nested = findDriver(file);
                if (nested != null) return nested;
            } else if (file.isFile() && file.getName().equalsIgnoreCase("libvulkan_freedreno.so")) {
                return file;
            }
        }
        return null;
    }

    private static void deleteTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        if (file.exists()) file.delete();
    }

    private void updateDriverStatus() {
        if (driverStatus == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String directory = prefs.getString("turnipDir", "");
        String name = prefs.getString("turnipName", "");
        File driver = directory.isEmpty() || name.isEmpty() ? null : new File(directory, name);
        if (driver != null && driver.isFile()) {
            driverStatus.setText("Importado para o armazenamento privado do app: " + driver.getAbsolutePath());
        } else {
            driverStatus.setText("Nenhum Turnip importado. Selecione a pasta extraída do driver em Downloads.");
            driverModeSpinner.setSelection(0);
        }
    }

    private Spinner addOption(LinearLayout page, String title, String[] values, String help) {
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Color.rgb(247, 190, 79));
        label.setTextSize(15);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(0, dp(15), 0, dp(4));
        page.addView(label);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        page.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView description = new TextView(this);
        description.setText(help);
        description.setTextColor(Color.rgb(173, 184, 195));
        description.setTextSize(12);
        description.setPadding(0, dp(1), 0, dp(2));
        page.addView(description);
        return spinner;
    }

    private void launchGame(boolean safeMode) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int driverMode = safeMode ? 0 : driverModeSpinner.getSelectedItemPosition();
        String directory = prefs.getString("turnipDir", "");
        String name = prefs.getString("turnipName", "");
        if (driverMode == 1 && (directory.isEmpty() || name.isEmpty() || !new File(directory, name).isFile())) {
            Toast.makeText(this, "Selecione primeiro uma pasta Turnip válida em Downloads.", Toast.LENGTH_LONG).show();
            return;
        }
        int gpu = safeMode ? 0 : gpuSpinner.getSelectedItemPosition();
        int resolution = safeMode ? 0 : resolutionSpinner.getSelectedItemPosition();
        int present = safeMode ? 0 : presentSpinner.getSelectedItemPosition();
        int frames = safeMode ? 0 : framesSpinner.getSelectedItemPosition();
        prefs.edit().putInt("driverMode", driverMode).putInt("gpu", gpu)
                .putInt("resolution", resolution).putInt("present", present)
                .putInt("frames", frames).apply();
        CastleApplication.log("SETUP_LAUNCH safe=" + safeMode + " driverMode=" + driverMode + " gpu=" + gpu
                + " resolution=" + resolution + " present=" + present + " frames=" + frames);
        startActivity(new Intent(this, CastleActivity.class));
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdown();
        super.onDestroy();
    }
}
