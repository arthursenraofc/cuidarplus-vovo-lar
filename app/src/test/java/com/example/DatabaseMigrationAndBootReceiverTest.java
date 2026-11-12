package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.example.database.DatabaseHelper;
import com.example.model.Medicamento;
import com.example.receiver.BootReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
public class DatabaseMigrationAndBootReceiverTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }

    @After
    public void tearDown() {
        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }

    @Test
    public void deveMigrarBancoV2SemColunaEstoqueERestaurarAlarmesNoBootSemErro() {
        // 1. Simula exatamente o banco legado v2 existente no dispositivo (sem a coluna m.estoque)
        File dbFile = context.getDatabasePath(DatabaseHelper.DATABASE_NAME);
        if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }

        try (SQLiteDatabase legacyDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)) {
            legacyDb.execSQL("CREATE TABLE idosos ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "nome TEXT NOT NULL, "
                    + "data_nascimento TEXT, "
                    + "tipo_sanguineo TEXT, "
                    + "alergias TEXT, "
                    + "restricoes_medicas TEXT, "
                    + "contato_emergencia TEXT, "
                    + "caminho_foto TEXT);");

            // Esquema antigo da v2 sem a coluna 'estoque'
            legacyDb.execSQL("CREATE TABLE medicamento ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "id_idoso INTEGER NOT NULL, "
                    + "nome_medicamento TEXT NOT NULL, "
                    + "dosagem TEXT NOT NULL, "
                    + "tipo TEXT NOT NULL DEFAULT 'Comprimido', "
                    + "horario_inicial TEXT NOT NULL, "
                    + "frequencia_horas INTEGER NOT NULL, "
                    + "observacao_medica TEXT, "
                    + "status_ativo INTEGER NOT NULL DEFAULT 1);");

            legacyDb.execSQL("CREATE TABLE historico_medicacao ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "id_medicamento INTEGER NOT NULL, "
                    + "id_idoso INTEGER NOT NULL, "
                    + "data_hora_prescrita TEXT, "
                    + "data_hora_executada TEXT NOT NULL, "
                    + "status TEXT NOT NULL, "
                    + "nome_cuidador TEXT, "
                    + "observacao TEXT);");

            legacyDb.execSQL("INSERT INTO idosos (id, nome) VALUES (1, 'Dona Francisca de Souza');");
            legacyDb.execSQL("INSERT INTO medicamento (id_idoso, nome_medicamento, dosagem, tipo, horario_inicial, frequencia_horas, observacao_medica, status_ativo) "
                    + "VALUES (1, 'Losartana Potássica', '50mg', 'Comprimido', '08:00', 12, 'Após café', 1);");
            legacyDb.setVersion(2);
        }

        // 2. Dispara o BootReceiver exatamente como ocorre em ACTION_BOOT_COMPLETED / ACTION_MY_PACKAGE_REPLACED
        BootReceiver bootReceiver = new BootReceiver();
        bootReceiver.onReceive(context, new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));

        // 3. Verifica que a migração para v3 ocorreu sem perda de dados e que m.estoque existe e responde às queries
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
        List<Medicamento> agendados = dbHelper.buscarMedicamentosAgendados();
        assertEquals(1, agendados.size());

        Medicamento medMigrado = agendados.get(0);
        assertEquals("Losartana Potássica", medMigrado.getNomeMedicamento());
        assertEquals("Dona Francisca de Souza", medMigrado.getNomeIdoso());
        assertNotNull(medMigrado.getEstoque());
        assertEquals("", medMigrado.getEstoque());

        // 4. Verifica atualização de estoque após migração
        medMigrado.setEstoque("30 comprimidos");
        int atualizados = dbHelper.atualizarMedicamento(medMigrado);
        assertEquals(1, atualizados);

        Medicamento recarregado = dbHelper.buscarMedicamentoPorId(medMigrado.getId());
        assertNotNull(recarregado);
        assertEquals("30 comprimidos", recarregado.getEstoque());
    }

    @Test
    public void deveAbrirEstoqueActivityComBancoLegadoSemColunaEstoqueSemCrash() {
        // Simula o banco legado v2 instalado no aparelho sem a coluna m.estoque
        File dbFile = context.getDatabasePath(DatabaseHelper.DATABASE_NAME);
        if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }

        try (SQLiteDatabase legacyDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)) {
            legacyDb.execSQL("CREATE TABLE idosos ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "nome TEXT NOT NULL, "
                    + "data_nascimento TEXT, "
                    + "tipo_sanguineo TEXT, "
                    + "alergias TEXT, "
                    + "restricoes_medicas TEXT, "
                    + "contato_emergencia TEXT, "
                    + "caminho_foto TEXT);");

            legacyDb.execSQL("CREATE TABLE medicamento ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "id_idoso INTEGER NOT NULL, "
                    + "nome_medicamento TEXT NOT NULL, "
                    + "dosagem TEXT NOT NULL, "
                    + "tipo TEXT NOT NULL DEFAULT 'Comprimido', "
                    + "horario_inicial TEXT NOT NULL, "
                    + "frequencia_horas INTEGER NOT NULL, "
                    + "observacao_medica TEXT, "
                    + "status_ativo INTEGER NOT NULL DEFAULT 1);");

            legacyDb.execSQL("CREATE TABLE historico_medicacao ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "id_medicamento INTEGER NOT NULL, "
                    + "id_idoso INTEGER NOT NULL, "
                    + "data_hora_prescrita TEXT, "
                    + "data_hora_executada TEXT NOT NULL, "
                    + "status TEXT NOT NULL, "
                    + "nome_cuidador TEXT, "
                    + "observacao TEXT);");

            legacyDb.execSQL("INSERT INTO idosos (id, nome, contato_emergencia) VALUES (1, 'Seu Antônio Carlos Ribeiro', '(51) 99123-8877');");
            legacyDb.execSQL("INSERT INTO medicamento (id_idoso, nome_medicamento, dosagem, tipo, horario_inicial, frequencia_horas, observacao_medica, status_ativo) "
                    + "VALUES (1, 'Prolopa', '200/50mg', 'Comprimido', '07:00', 6, 'Antes das refeições', 1);");
            legacyDb.setVersion(2);
        }

        // Abre a EstoqueActivity completa (onCreate -> onStart -> onResume -> carregarDadosEstoque -> listarTodosMedicamentos)
        try (org.robolectric.android.controller.ActivityController<EstoqueActivity> controller =
                     org.robolectric.Robolectric.buildActivity(EstoqueActivity.class).setup()) {
            EstoqueActivity activity = controller.get();
            assertNotNull(activity);

            android.widget.TextView tvTotal = activity.findViewById(R.id.tv_resumo_total_itens);
            assertNotNull(tvTotal);
            assertEquals("1 medicamento registrado", tvTotal.getText().toString());
        }
    }

    @Test
    public void deveCriarBancoNovoComColunaEstoquePopuladaNosDadosIniciais() {
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
        List<Medicamento> todos = dbHelper.listarTodosMedicamentos();

        assertFalse(todos.isEmpty());
        for (Medicamento med : todos) {
            assertNotNull(med.getEstoque());
            assertFalse(med.getEstoque().isEmpty());
        }
    }
}
