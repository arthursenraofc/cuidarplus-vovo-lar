package com.example.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.model.DoseAlertaDTO;
import com.example.model.HistoricoMedicacao;
import com.example.model.Medicamento;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Gerenciador do Banco de Dados Local SQLite para a ILPI (Sprint 1 & Sprint 2 Avançado).
 * Controla tabelas de idosos, medicamentos com tipos/observações e histórico clínico de medicação.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    // Configurações do Banco de Dados
    public static final String DATABASE_NAME = "lar_idosos.db";
    public static final int DATABASE_VERSION = 5;

    // Tabela Idosos
    public static final String TABLE_IDOSOS = "idosos";
    public static final String COL_ID = "id";
    public static final String COL_NOME = "nome";
    public static final String COL_DATA_NASCIMENTO = "data_nascimento";
    public static final String COL_TIPO_SANGUINEO = "tipo_sanguineo";
    public static final String COL_ALERGIAS = "alergias";
    public static final String COL_RESTRICOES_MEDICAS = "restricoes_medicas";
    public static final String COL_CONTATO_EMERGENCIA = "contato_emergencia";
    public static final String COL_CAMINHO_FOTO = "caminho_foto";
    public static final String COL_CPF = "cpf";
    public static final String COL_LEITO_QUARTO = "leito_quarto";
    public static final String COL_NOME_FAMILIAR = "nome_familiar";
    public static final String COL_GRAU_PARENTESCO = "grau_parentesco";

    // Tabela Medicamentos (Sprint 2)
    public static final String TABLE_MEDICAMENTO = "medicamento";
    public static final String COL_MED_ID = "id";
    public static final String COL_MED_ID_IDOSO = "id_idoso";
    public static final String COL_MED_NOME = "nome_medicamento";
    public static final String COL_MED_DOSAGEM = "dosagem";
    public static final String COL_MED_ESTOQUE = "estoque";
    public static final String COL_MED_TIPO = "tipo";
    public static final String COL_MED_HORARIO_INICIAL = "horario_inicial";
    public static final String COL_MED_FREQUENCIA_HORAS = "frequencia_horas";
    public static final String COL_MED_OBSERVACAO = "observacao_medica";
    public static final String COL_MED_STATUS_ATIVO = "status_ativo";

    // Tabela Histórico de Medicação (Sprint 2)
    public static final String TABLE_HISTORICO_MEDICACAO = "historico_medicacao";
    public static final String COL_HIST_ID = "id";
    public static final String COL_HIST_ID_MEDICAMENTO = "id_medicamento";
    public static final String COL_HIST_ID_IDOSO = "id_idoso";
    public static final String COL_HIST_DATA_HORA_PRESCRITA = "data_hora_prescrita";
    public static final String COL_HIST_DATA_HORA_EXECUTADA = "data_hora_executada";
    public static final String COL_HIST_STATUS = "status";
    public static final String COL_HIST_NOME_CUIDADOR = "nome_cuidador";
    public static final String COL_HIST_OBSERVACAO = "observacao";

    // SQL de Criação da Tabela Idosos
    private static final String CREATE_TABLE_IDOSOS = "CREATE TABLE IF NOT EXISTS " + TABLE_IDOSOS + " ("
            + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_NOME + " TEXT NOT NULL, "
            + COL_DATA_NASCIMENTO + " TEXT, "
            + COL_TIPO_SANGUINEO + " TEXT, "
            + COL_ALERGIAS + " TEXT, "
            + COL_RESTRICOES_MEDICAS + " TEXT, "
            + COL_CONTATO_EMERGENCIA + " TEXT, "
            + COL_CAMINHO_FOTO + " TEXT, "
            + COL_CPF + " TEXT DEFAULT '', "
            + COL_LEITO_QUARTO + " TEXT DEFAULT '', "
            + COL_NOME_FAMILIAR + " TEXT DEFAULT '', "
            + COL_GRAU_PARENTESCO + " TEXT DEFAULT '');";

    // SQL de Criação da Tabela Medicamento
    private static final String CREATE_TABLE_MEDICAMENTO = "CREATE TABLE IF NOT EXISTS " + TABLE_MEDICAMENTO + " ("
            + COL_MED_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_MED_ID_IDOSO + " INTEGER NOT NULL, "
            + COL_MED_NOME + " TEXT NOT NULL, "
            + COL_MED_DOSAGEM + " TEXT NOT NULL, "
            + COL_MED_ESTOQUE + " TEXT DEFAULT '', "
            + COL_MED_TIPO + " TEXT NOT NULL DEFAULT 'Comprimido', "
            + COL_MED_HORARIO_INICIAL + " TEXT NOT NULL, "
            + COL_MED_FREQUENCIA_HORAS + " INTEGER NOT NULL DEFAULT 8, "
            + COL_MED_OBSERVACAO + " TEXT DEFAULT '', "
            + COL_MED_STATUS_ATIVO + " INTEGER NOT NULL DEFAULT 1, "
            + "FOREIGN KEY(" + COL_MED_ID_IDOSO + ") REFERENCES " + TABLE_IDOSOS + "(" + COL_ID + ") ON DELETE CASCADE);";

    // SQL de Criação da Tabela Histórico de Medicação
    private static final String CREATE_TABLE_HISTORICO_MEDICACAO = "CREATE TABLE IF NOT EXISTS " + TABLE_HISTORICO_MEDICACAO + " ("
            + COL_HIST_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_HIST_ID_MEDICAMENTO + " INTEGER NOT NULL, "
            + COL_HIST_ID_IDOSO + " INTEGER NOT NULL, "
            + COL_HIST_DATA_HORA_PRESCRITA + " TEXT, "
            + COL_HIST_DATA_HORA_EXECUTADA + " TEXT NOT NULL, "
            + COL_HIST_STATUS + " TEXT NOT NULL, "
            + COL_HIST_NOME_CUIDADOR + " TEXT, "
            + COL_HIST_OBSERVACAO + " TEXT, "
            + "FOREIGN KEY(" + COL_HIST_ID_MEDICAMENTO + ") REFERENCES " + TABLE_MEDICAMENTO + "(" + COL_MED_ID + ") ON DELETE CASCADE, "
            + "FOREIGN KEY(" + COL_HIST_ID_IDOSO + ") REFERENCES " + TABLE_IDOSOS + "(" + COL_ID + ") ON DELETE CASCADE);";

    private static DatabaseHelper instance;
    private volatile boolean esquemaVerificado = false;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Reinicia a instância singleton para testes automatizados locais (Robolectric).
     */
    public static synchronized void resetInstanceForTesting() {
        if (instance != null) {
            try {
                instance.close();
            } catch (Exception ignored) {
            }
            instance = null;
        }
    }

    public DatabaseHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public synchronized void close() {
        esquemaVerificado = false;
        super.close();
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_IDOSOS);
        db.execSQL(CREATE_TABLE_MEDICAMENTO);
        db.execSQL(CREATE_TABLE_HISTORICO_MEDICACAO);

        inserirDadosIniciais(db);
        esquemaVerificado = true;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Garante primeiro que todas as tabelas e colunas novas (CPF, leito, familiar, estoque) existam
        garantirEsquemaAtualizado(db);

        // Migração da v1 (Sprint 1) para a versão completa atualizada
        if (oldVersion < 2) {
            garantirAniversariantePadraoSeAusente(db);
            inserirMedicamentosIniciaisSeVazio(db);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (!db.isReadOnly()) {
            garantirEsquemaAtualizado(db);
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            garantirEsquemaAtualizado(db);
        }
    }

    /**
     * Obtém conexão com o SQLite garantindo que o esquema (incluindo m.estoque) esteja atualizado.
     */
    private SQLiteDatabase obterBancoComEsquemaGarantido() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            if (!esquemaVerificado && !db.isReadOnly()) {
                garantirEsquemaAtualizado(db);
            }
            return db;
        } catch (Exception e) {
            return getReadableDatabase();
        }
    }

    /**
     * Verifica de forma idempotente via PRAGMA table_info se todas as tabelas e colunas
     * esperadas pelas queries existem no SQLite local, adicionando colunas ausentes sem perda de dados.
     */
    private synchronized void garantirEsquemaAtualizado(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_IDOSOS);
        db.execSQL(CREATE_TABLE_MEDICAMENTO);
        db.execSQL(CREATE_TABLE_HISTORICO_MEDICACAO);

        // Colunas da tabela idosos
        adicionarColunaSeAusente(db, TABLE_IDOSOS, COL_DATA_NASCIMENTO, "TEXT");
        adicionarColunaSeAusente(db, TABLE_IDOSOS, COL_TIPO_SANGUINEO, "TEXT");
        adicionarColunaSeAusente(db, TABLE_IDOSOS, COL_ALERGIAS, "TEXT");
        adicionarColunaSeAusente(db, TABLE_IDOSOS, COL_RESTRICOES_MEDICAS, "TEXT");
        adicionarColunaSeAusente(db, TABLE_IDOSOS, COL_CONTATO_EMERGENCIA, "TEXT");
        adicionarColunaSeAusente(db, TABLE_IDOSOS, COL_CAMINHO_FOTO, "TEXT");
        adicionarColunaSeAusente(db, TABLE_IDOSOS, COL_CPF, "TEXT DEFAULT ''");
        adicionarColunaSeAusente(db, TABLE_IDOSOS, COL_LEITO_QUARTO, "TEXT DEFAULT ''");
        adicionarColunaSeAusente(db, TABLE_IDOSOS, COL_NOME_FAMILIAR, "TEXT DEFAULT ''");
        adicionarColunaSeAusente(db, TABLE_IDOSOS, COL_GRAU_PARENTESCO, "TEXT DEFAULT ''");

        // Colunas da tabela medicamento (inclui correção definitiva para m.estoque)
        adicionarColunaSeAusente(db, TABLE_MEDICAMENTO, COL_MED_ESTOQUE, "TEXT DEFAULT ''");
        adicionarColunaSeAusente(db, TABLE_MEDICAMENTO, COL_MED_TIPO, "TEXT NOT NULL DEFAULT 'Comprimido'");
        adicionarColunaSeAusente(db, TABLE_MEDICAMENTO, COL_MED_HORARIO_INICIAL, "TEXT NOT NULL DEFAULT '08:00'");
        adicionarColunaSeAusente(db, TABLE_MEDICAMENTO, COL_MED_FREQUENCIA_HORAS, "INTEGER NOT NULL DEFAULT 8");
        adicionarColunaSeAusente(db, TABLE_MEDICAMENTO, COL_MED_OBSERVACAO, "TEXT DEFAULT ''");
        adicionarColunaSeAusente(db, TABLE_MEDICAMENTO, COL_MED_STATUS_ATIVO, "INTEGER NOT NULL DEFAULT 1");

        // Colunas da tabela historico_medicacao
        adicionarColunaSeAusente(db, TABLE_HISTORICO_MEDICACAO, COL_HIST_DATA_HORA_PRESCRITA, "TEXT");
        adicionarColunaSeAusente(db, TABLE_HISTORICO_MEDICACAO, COL_HIST_NOME_CUIDADOR, "TEXT");
        adicionarColunaSeAusente(db, TABLE_HISTORICO_MEDICACAO, COL_HIST_OBSERVACAO, "TEXT");

        // Normaliza registros legados cujo estoque estava nulo após ALTER TABLE
        db.execSQL("UPDATE " + TABLE_MEDICAMENTO + " SET " + COL_MED_ESTOQUE + " = '' WHERE " + COL_MED_ESTOQUE + " IS NULL");

        // Enriquece de forma não-destrutiva campos ausentes nos cadastros padrão vindos de versões anteriores
        enriquecerCamposLegadosIdososPadrao(db);
        esquemaVerificado = true;
    }

    /**
     * Completa campos adicionados nas versões recentes (CPF, leito, familiar, parentesco, WhatsApp)
     * apenas onde estiverem vazios/nulos nos residentes iniciais padrão, preservando edições do usuário.
     */
    private void enriquecerCamposLegadosIdososPadrao(SQLiteDatabase db) {
        preencherDadosPadraoResidenteSeVazio(
                db,
                "Dona Francisca de Souza",
                "Filha Mariana - (51) 98765-4321",
                "184.520.390-15",
                "Quarto 01 - Leito A",
                "Mariana de Souza",
                "Filha"
        );
        preencherDadosPadraoResidenteSeVazio(
                db,
                "Seu Antônio Carlos Ribeiro",
                "Filho Roberto - (51) 99123-8877",
                "092.418.760-44",
                "Quarto 02 - Leito B",
                "Roberto Ribeiro",
                "Filho"
        );
        preencherDadosPadraoResidenteSeVazio(
                db,
                "Dona Tereza Cristina Moreira",
                "Irmã Helena - (51) 98844-3322",
                "315.670.120-87",
                "Quarto 03 - Leito A",
                "Helena Moreira",
                "Irmã"
        );
        preencherDadosPadraoResidenteSeVazio(
                db,
                "Seu Joaquim Augusto Silveira",
                "Neta Camila - (51) 99654-1122",
                "428.190.530-22",
                "Quarto 04 - Leito A",
                "Camila Silveira",
                "Neta"
        );

        // Mantém o mês do aniversariante demonstrativo alinhado ao mês corrente para o alerta do Dashboard
        int mesAtual = Calendar.getInstance().get(Calendar.MONTH) + 1;
        String dataAniversarioMesAtual = String.format(Locale.US, "25/%02d/1941", mesAtual);
        db.execSQL(
                "UPDATE " + TABLE_IDOSOS
                        + " SET " + COL_DATA_NASCIMENTO + " = ?"
                        + " WHERE " + COL_NOME + " = ? AND " + COL_CPF + " = ? AND " + COL_DATA_NASCIMENTO + " LIKE '25/%/1941'",
                new Object[]{dataAniversarioMesAtual, "Seu Joaquim Augusto Silveira", "428.190.530-22"}
        );
    }

    private void preencherDadosPadraoResidenteSeVazio(
            SQLiteDatabase db,
            String nome,
            String contatoPadrao,
            String cpfPadrao,
            String leitoPadrao,
            String familiarPadrao,
            String parentescoPadrao
    ) {
        db.execSQL(
                "UPDATE " + TABLE_IDOSOS + " SET "
                        + COL_CONTATO_EMERGENCIA + " = CASE WHEN " + COL_CONTATO_EMERGENCIA + " IS NULL OR TRIM(" + COL_CONTATO_EMERGENCIA + ") = '' THEN ? ELSE " + COL_CONTATO_EMERGENCIA + " END, "
                        + COL_CPF + " = CASE WHEN " + COL_CPF + " IS NULL OR TRIM(" + COL_CPF + ") = '' THEN ? ELSE " + COL_CPF + " END, "
                        + COL_LEITO_QUARTO + " = CASE WHEN " + COL_LEITO_QUARTO + " IS NULL OR TRIM(" + COL_LEITO_QUARTO + ") = '' THEN ? ELSE " + COL_LEITO_QUARTO + " END, "
                        + COL_NOME_FAMILIAR + " = CASE WHEN " + COL_NOME_FAMILIAR + " IS NULL OR TRIM(" + COL_NOME_FAMILIAR + ") = '' THEN ? ELSE " + COL_NOME_FAMILIAR + " END, "
                        + COL_GRAU_PARENTESCO + " = CASE WHEN " + COL_GRAU_PARENTESCO + " IS NULL OR TRIM(" + COL_GRAU_PARENTESCO + ") = '' THEN ? ELSE " + COL_GRAU_PARENTESCO + " END "
                        + "WHERE " + COL_NOME + " = ?",
                new Object[]{contatoPadrao, cpfPadrao, leitoPadrao, familiarPadrao, parentescoPadrao, nome}
        );
    }

    private void garantirAniversariantePadraoSeAusente(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_IDOSOS + " WHERE " + COL_NOME + " = ?",
                new String[]{"Seu Joaquim Augusto Silveira"}
        )) {
            if (c != null && c.moveToFirst() && c.getInt(0) > 0) {
                return;
            }
        }
        int mesAtual = Calendar.getInstance().get(Calendar.MONTH) + 1;
        String dataAniversarioMesAtual = String.format(Locale.US, "25/%02d/1941", mesAtual);

        ContentValues cv4 = new ContentValues();
        cv4.put(COL_NOME, "Seu Joaquim Augusto Silveira");
        cv4.put(COL_DATA_NASCIMENTO, dataAniversarioMesAtual);
        cv4.put(COL_TIPO_SANGUINEO, "AB+");
        cv4.put(COL_ALERGIAS, "Nenhuma alergia medicamentosa");
        cv4.put(COL_RESTRICOES_MEDICAS, "Acompanhamento cardiológico preventivo e caminhada assistida.");
        cv4.put(COL_CONTATO_EMERGENCIA, "Neta Camila - (51) 99654-1122");
        cv4.put(COL_CAMINHO_FOTO, "");
        cv4.put(COL_CPF, "428.190.530-22");
        cv4.put(COL_LEITO_QUARTO, "Quarto 04 - Leito A");
        cv4.put(COL_NOME_FAMILIAR, "Camila Silveira");
        cv4.put(COL_GRAU_PARENTESCO, "Neta");
        db.insert(TABLE_IDOSOS, null, cv4);
    }

    private void inserirMedicamentosIniciaisSeVazio(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MEDICAMENTO, null)) {
            if (c != null && c.moveToFirst() && c.getInt(0) > 0) {
                return;
            }
        }
        inserirMedicamentosIniciais(db);
    }

    private void adicionarColunaSeAusente(SQLiteDatabase db, String tabela, String coluna, String definicaoSql) {
        if (!colunaExiste(db, tabela, coluna)) {
            db.execSQL("ALTER TABLE " + tabela + " ADD COLUMN " + coluna + " " + definicaoSql);
        }
    }

    private boolean colunaExiste(SQLiteDatabase db, String tabela, String coluna) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + tabela + ")", null)) {
            if (cursor != null) {
                int nameIdx = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    if (nameIdx != -1 && coluna.equalsIgnoreCase(cursor.getString(nameIdx))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void inserirDadosIniciais(SQLiteDatabase db) {
        ContentValues cv1 = new ContentValues();
        cv1.put(COL_NOME, "Dona Francisca de Souza");
        cv1.put(COL_DATA_NASCIMENTO, "15/04/1945");
        cv1.put(COL_TIPO_SANGUINEO, "O+");
        cv1.put(COL_ALERGIAS, "Alergia severa a Penicilina e Dipirona");
        cv1.put(COL_RESTRICOES_MEDICAS, "Hipertensão arterial, Diabetes Tipo 2. Dieta hipossódica.");
        cv1.put(COL_CONTATO_EMERGENCIA, "Filha Mariana - (51) 98765-4321");
        cv1.put(COL_CAMINHO_FOTO, "");
        cv1.put(COL_CPF, "184.520.390-15");
        cv1.put(COL_LEITO_QUARTO, "Quarto 01 - Leito A");
        cv1.put(COL_NOME_FAMILIAR, "Mariana de Souza");
        cv1.put(COL_GRAU_PARENTESCO, "Filha");
        db.insert(TABLE_IDOSOS, null, cv1);

        ContentValues cv2 = new ContentValues();
        cv2.put(COL_NOME, "Seu Antônio Carlos Ribeiro");
        cv2.put(COL_DATA_NASCIMENTO, "08/11/1939");
        cv2.put(COL_TIPO_SANGUINEO, "A+");
        cv2.put(COL_ALERGIAS, "Nenhuma alergia conhecida");
        cv2.put(COL_RESTRICOES_MEDICAS, "Mal de Parkinson inicial. Risco moderado de quedas. Uso de andador.");
        cv2.put(COL_CONTATO_EMERGENCIA, "Filho Roberto - (51) 99123-8877");
        cv2.put(COL_CAMINHO_FOTO, "");
        cv2.put(COL_CPF, "092.418.760-44");
        cv2.put(COL_LEITO_QUARTO, "Quarto 02 - Leito B");
        cv2.put(COL_NOME_FAMILIAR, "Roberto Ribeiro");
        cv2.put(COL_GRAU_PARENTESCO, "Filho");
        db.insert(TABLE_IDOSOS, null, cv2);

        ContentValues cv3 = new ContentValues();
        cv3.put(COL_NOME, "Dona Tereza Cristina Moreira");
        cv3.put(COL_DATA_NASCIMENTO, "22/07/1942");
        cv3.put(COL_TIPO_SANGUINEO, "B-");
        cv3.put(COL_ALERGIAS, "Intolerância severa à lactose e frutos do mar");
        cv3.put(COL_RESTRICOES_MEDICAS, "Osteoporose avançada. Fisioterapia motora 3x por semana.");
        cv3.put(COL_CONTATO_EMERGENCIA, "Irmã Helena - (51) 98844-3322");
        cv3.put(COL_CAMINHO_FOTO, "");
        cv3.put(COL_CPF, "315.670.120-87");
        cv3.put(COL_LEITO_QUARTO, "Quarto 03 - Leito A");
        cv3.put(COL_NOME_FAMILIAR, "Helena Moreira");
        cv3.put(COL_GRAU_PARENTESCO, "Irmã");
        db.insert(TABLE_IDOSOS, null, cv3);

        int mesAtual = Calendar.getInstance().get(Calendar.MONTH) + 1;
        String dataAniversarioMesAtual = String.format(Locale.US, "25/%02d/1941", mesAtual);

        ContentValues cv4 = new ContentValues();
        cv4.put(COL_NOME, "Seu Joaquim Augusto Silveira");
        cv4.put(COL_DATA_NASCIMENTO, dataAniversarioMesAtual);
        cv4.put(COL_TIPO_SANGUINEO, "AB+");
        cv4.put(COL_ALERGIAS, "Nenhuma alergia medicamentosa");
        cv4.put(COL_RESTRICOES_MEDICAS, "Acompanhamento cardiológico preventivo e caminhada assistida.");
        cv4.put(COL_CONTATO_EMERGENCIA, "Neta Camila - (51) 99654-1122");
        cv4.put(COL_CAMINHO_FOTO, "");
        cv4.put(COL_CPF, "428.190.530-22");
        cv4.put(COL_LEITO_QUARTO, "Quarto 04 - Leito A");
        cv4.put(COL_NOME_FAMILIAR, "Camila Silveira");
        cv4.put(COL_GRAU_PARENTESCO, "Neta");
        db.insert(TABLE_IDOSOS, null, cv4);

        inserirMedicamentosIniciais(db);
    }

    private void inserirMedicamentosIniciais(SQLiteDatabase db) {
        ContentValues m1 = new ContentValues();
        m1.put(COL_MED_ID_IDOSO, 1);
        m1.put(COL_MED_NOME, "Losartana Potássica");
        m1.put(COL_MED_DOSAGEM, "50mg (1 comprimido)");
        m1.put(COL_MED_ESTOQUE, "28 comprimidos");
        m1.put(COL_MED_TIPO, "Comprimido");
        m1.put(COL_MED_HORARIO_INICIAL, "08:00");
        m1.put(COL_MED_FREQUENCIA_HORAS, 12);
        m1.put(COL_MED_OBSERVACAO, "Administrar com copo cheio de água após café da manhã");
        m1.put(COL_MED_STATUS_ATIVO, 1);
        db.insert(TABLE_MEDICAMENTO, null, m1);

        ContentValues m2 = new ContentValues();
        m2.put(COL_MED_ID_IDOSO, 1);
        m2.put(COL_MED_NOME, "Metformina");
        m2.put(COL_MED_DOSAGEM, "850mg (1 comprimido)");
        m2.put(COL_MED_ESTOQUE, "45 comprimidos");
        m2.put(COL_MED_TIPO, "Comprimido");
        m2.put(COL_MED_HORARIO_INICIAL, "09:00");
        m2.put(COL_MED_FREQUENCIA_HORAS, 8);
        m2.put(COL_MED_OBSERVACAO, "Tomar imediatamente após refeição principal");
        m2.put(COL_MED_STATUS_ATIVO, 1);
        db.insert(TABLE_MEDICAMENTO, null, m2);

        ContentValues m3 = new ContentValues();
        m3.put(COL_MED_ID_IDOSO, 2);
        m3.put(COL_MED_NOME, "Prolopa (Levodopa + Benserazida)");
        m3.put(COL_MED_DOSAGEM, "200/50mg (1 comprimido)");
        m3.put(COL_MED_ESTOQUE, "4 comprimidos");
        m3.put(COL_MED_TIPO, "Comprimido");
        m3.put(COL_MED_HORARIO_INICIAL, "07:00");
        m3.put(COL_MED_FREQUENCIA_HORAS, 6);
        m3.put(COL_MED_OBSERVACAO, "Ingerir 30min antes da alimentação para melhor absorção");
        m3.put(COL_MED_STATUS_ATIVO, 1);
        db.insert(TABLE_MEDICAMENTO, null, m3);
    }

    // ==========================================
    // MÉTODOS DAO AVANÇADOS (Sprint 2)
    // ==========================================

    /**
     * Cadastra um novo medicamento no SQLite.
     */
    public long cadastrarMedicamento(Medicamento med) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_MED_ID_IDOSO, med.getIdIdoso());
        cv.put(COL_MED_NOME, med.getNomeMedicamento());
        cv.put(COL_MED_DOSAGEM, med.getDosagem());
        cv.put(COL_MED_ESTOQUE, med.getEstoque());
        cv.put(COL_MED_TIPO, med.getTipo());
        cv.put(COL_MED_HORARIO_INICIAL, med.getHorarioInicial());
        cv.put(COL_MED_FREQUENCIA_HORAS, med.getFrequenciaHoras());
        cv.put(COL_MED_OBSERVACAO, med.getObservacaoMedica());
        cv.put(COL_MED_STATUS_ATIVO, med.getStatusAtivo());

        long id = db.insert(TABLE_MEDICAMENTO, null, cv);
        if (id != -1) {
            med.setId((int) id);
        }
        return id;
    }

    /**
     * Atualiza os dados de um medicamento existente no SQLite.
     * @param med Objeto medicamento com ID e novos dados
     * @return Linhas afetadas
     */
    public int atualizarMedicamento(Medicamento med) {
        if (med == null || med.getId() <= 0) return 0;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_MED_ID_IDOSO, med.getIdIdoso());
        cv.put(COL_MED_NOME, med.getNomeMedicamento());
        cv.put(COL_MED_DOSAGEM, med.getDosagem());
        cv.put(COL_MED_ESTOQUE, med.getEstoque());
        cv.put(COL_MED_TIPO, med.getTipo());
        cv.put(COL_MED_HORARIO_INICIAL, med.getHorarioInicial());
        cv.put(COL_MED_FREQUENCIA_HORAS, med.getFrequenciaHoras());
        cv.put(COL_MED_OBSERVACAO, med.getObservacaoMedica());
        cv.put(COL_MED_STATUS_ATIVO, med.getStatusAtivo());

        return db.update(
                TABLE_MEDICAMENTO,
                cv,
                COL_MED_ID + " = ?",
                new String[]{String.valueOf(med.getId())}
        );
    }

    /**
     * Método DAO avançado: registrarAplicacao()
     * Registra a aplicação/baixa de medicamento com status ("ADMINISTRADO", "ATRASADO", "RECUSADO"),
     * horários e observações.
     */
    public long registrarAplicacao(HistoricoMedicacao historico) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_HIST_ID_MEDICAMENTO, historico.getIdMedicamento());
        cv.put(COL_HIST_ID_IDOSO, historico.getIdIdoso());
        cv.put(COL_HIST_DATA_HORA_PRESCRITA, historico.getDataHoraPrescrita());
        cv.put(COL_HIST_DATA_HORA_EXECUTADA, historico.getDataHoraExecutada());
        cv.put(COL_HIST_STATUS, historico.getStatus());
        cv.put(COL_HIST_NOME_CUIDADOR, historico.getNomeCuidador());
        cv.put(COL_HIST_OBSERVACAO, historico.getObservacao());

        return db.insert(TABLE_HISTORICO_MEDICACAO, null, cv);
    }

    /**
     * Compatibilidade com versão anterior: registrarBaixaMedicacao()
     */
    public long registrarBaixaMedicacao(HistoricoMedicacao historico) {
        return registrarAplicacao(historico);
    }

    /**
     * Executa consultas de medicamentos garantindo recuperação imediata caso alguma coluna legada
     * (como m.estoque) esteja ausente ou em conexões somente-leitura.
     */
    private List<Medicamento> consultarMedicamentosSeguro(String clausulaJoinEWhere, String[] args) {
        List<Medicamento> lista = new ArrayList<>();
        String projecaoCompleta = "SELECT m." + COL_MED_ID + ", m." + COL_MED_ID_IDOSO + ", m." + COL_MED_NOME + ", "
                + "m." + COL_MED_DOSAGEM + ", m." + COL_MED_ESTOQUE + ", m." + COL_MED_TIPO + ", m." + COL_MED_HORARIO_INICIAL + ", "
                + "m." + COL_MED_FREQUENCIA_HORAS + ", m." + COL_MED_OBSERVACAO + ", "
                + "m." + COL_MED_STATUS_ATIVO + ", i." + COL_NOME + " AS nome_idoso "
                + "FROM " + TABLE_MEDICAMENTO + " m "
                + clausulaJoinEWhere;

        SQLiteDatabase db = obterBancoComEsquemaGarantido();
        try (Cursor cursor = db.rawQuery(projecaoCompleta, args)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    lista.add(extrairMedicamentoCursor(cursor));
                } while (cursor.moveToNext());
            }
            return lista;
        } catch (android.database.sqlite.SQLiteException ex) {
            // Auto-recuperação caso o banco estivesse aberto sem a coluna estoque
            try {
                SQLiteDatabase writableDb = getWritableDatabase();
                if (!writableDb.isReadOnly()) {
                    garantirEsquemaAtualizado(writableDb);
                    try (Cursor retryCursor = writableDb.rawQuery(projecaoCompleta, args)) {
                        if (retryCursor != null && retryCursor.moveToFirst()) {
                            do {
                                lista.add(extrairMedicamentoCursor(retryCursor));
                            } while (retryCursor.moveToNext());
                        }
                        return lista;
                    }
                }
            } catch (Exception ignored) {
            }

            // Fallback seguro caso o armazenamento esteja estritamente somente-leitura
            String projecaoFallback = "SELECT m." + COL_MED_ID + ", m." + COL_MED_ID_IDOSO + ", m." + COL_MED_NOME + ", "
                    + "m." + COL_MED_DOSAGEM + ", '' AS " + COL_MED_ESTOQUE + ", m." + COL_MED_TIPO + ", m." + COL_MED_HORARIO_INICIAL + ", "
                    + "m." + COL_MED_FREQUENCIA_HORAS + ", m." + COL_MED_OBSERVACAO + ", "
                    + "m." + COL_MED_STATUS_ATIVO + ", i." + COL_NOME + " AS nome_idoso "
                    + "FROM " + TABLE_MEDICAMENTO + " m "
                    + clausulaJoinEWhere;

            try (Cursor fallbackCursor = db.rawQuery(projecaoFallback, args)) {
                if (fallbackCursor != null && fallbackCursor.moveToFirst()) {
                    do {
                        lista.add(extrairMedicamentoCursor(fallbackCursor));
                    } while (fallbackCursor.moveToNext());
                }
            }
            return lista;
        }
    }

    /**
     * Lista todos os medicamentos de um idoso.
     */
    public List<Medicamento> listarMedicamentosPorIdoso(int idIdoso) {
        String clausula = "INNER JOIN " + TABLE_IDOSOS + " i ON m." + COL_MED_ID_IDOSO + " = i." + COL_ID + " "
                + "WHERE m." + COL_MED_ID_IDOSO + " = ? "
                + "ORDER BY m." + COL_MED_HORARIO_INICIAL + " ASC";
        return consultarMedicamentosSeguro(clausula, new String[]{String.valueOf(idIdoso)});
    }

    /**
     * Busca um medicamento por seu ID com dados completos.
     */
    public Medicamento buscarMedicamentoPorId(int idMedicamento) {
        String clausula = "LEFT JOIN " + TABLE_IDOSOS + " i ON m." + COL_MED_ID_IDOSO + " = i." + COL_ID + " "
                + "WHERE m." + COL_MED_ID + " = ?";
        List<Medicamento> encontrados = consultarMedicamentosSeguro(clausula, new String[]{String.valueOf(idMedicamento)});
        return encontrados.isEmpty() ? null : encontrados.get(0);
    }

    /**
     * Busca todos os medicamentos ativos cadastrados no sistema.
     */
    public List<Medicamento> buscarMedicamentosAgendados() {
        String clausula = "INNER JOIN " + TABLE_IDOSOS + " i ON m." + COL_MED_ID_IDOSO + " = i." + COL_ID + " "
                + "WHERE m." + COL_MED_STATUS_ATIVO + " = 1 "
                + "ORDER BY m." + COL_MED_HORARIO_INICIAL + " ASC";
        return consultarMedicamentosSeguro(clausula, null);
    }

    /**
     * Lista todos os medicamentos cadastrados de todos os residentes para controle de estoque e relatórios.
     */
    public List<Medicamento> listarTodosMedicamentos() {
        String clausula = "LEFT JOIN " + TABLE_IDOSOS + " i ON m." + COL_MED_ID_IDOSO + " = i." + COL_ID + " "
                + "ORDER BY m." + COL_MED_NOME + " ASC";
        return consultarMedicamentosSeguro(clausula, null);
    }

    /**
     * Método DAO avançado: buscarProximasDoses(id_idoso)
     * Retorna lista calculada das doses de um idoso para o dia com cálculo de pontualidade
     * ("NO_HORARIO", "ATRASADO", "CONCLUIDO").
     */
    public List<DoseAlertaDTO> buscarProximasDoses(int idIdoso) {
        List<Medicamento> medicamentos = listarMedicamentosPorIdoso(idIdoso);
        return calcularDosesEStatus(medicamentos);
    }

    /**
     * Método DAO avançado: buscarAlertasDoDia()
     * Retorna a lista de todas as doses ativas de todos os residentes para o dia,
     * calculando o status de pontualidade (Verde = No Horário, Vermelho = Atrasado, Cinza = Concluído).
     */
    public List<DoseAlertaDTO> buscarAlertasDoDia() {
        List<Medicamento> medicamentos = buscarMedicamentosAgendados();
        return calcularDosesEStatus(medicamentos);
    }

    /**
     * Helper para cálculo das próximas doses e classificação de pontualidade.
     */
    private List<DoseAlertaDTO> calcularDosesEStatus(List<Medicamento> medicamentos) {
        List<DoseAlertaDTO> resultado = new ArrayList<>();
        SimpleDateFormat sdfData = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String hojeStr = sdfData.format(new Date());

        Calendar agora = Calendar.getInstance();
        int horaAtual = agora.get(Calendar.HOUR_OF_DAY);
        int minutoAtual = agora.get(Calendar.MINUTE);
        int agoraMinutos = (horaAtual * 60) + minutoAtual;

        for (Medicamento med : medicamentos) {
            String horarioPrevisto = med.getHorarioInicial();
            boolean administradoHoje = verificarSeAdministradoHoje(med.getId(), hojeStr);

            String statusPontualidade;
            if (administradoHoje) {
                statusPontualidade = "CONCLUIDO";
            } else {
                int minutosPrevistos = extrairMinutosDoHorario(horarioPrevisto);
                // Se passou mais de 15 minutos do horário previsto sem ter sido administrado = ATRASADO
                if (agoraMinutos > minutosPrevistos + 15) {
                    statusPontualidade = "ATRASADO";
                } else {
                    statusPontualidade = "NO_HORARIO";
                }
            }

            resultado.add(new DoseAlertaDTO(med, horarioPrevisto, statusPontualidade, administradoHoje));
        }

        return resultado;
    }

    /**
     * Verifica no histórico se já houve registro de dose administrada para o medicamento hoje.
     */
    private boolean verificarSeAdministradoHoje(int idMedicamento, String dataHojeStr) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT COUNT(*) FROM " + TABLE_HISTORICO_MEDICACAO + " "
                + "WHERE " + COL_HIST_ID_MEDICAMENTO + " = ? "
                + "AND " + COL_HIST_STATUS + " = 'ADMINISTRADO' "
                + "AND " + COL_HIST_DATA_HORA_EXECUTADA + " LIKE ?";

        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(idMedicamento), "%" + dataHojeStr + "%"})) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0) > 0;
            }
        }
        return false;
    }

    private int extrairMinutosDoHorario(String horario) {
        try {
            String[] partes = horario.split(":");
            int h = Integer.parseInt(partes[0].trim());
            int m = Integer.parseInt(partes[1].trim());
            return (h * 60) + m;
        } catch (Exception e) {
            return 0;
        }
    }

    private Medicamento extrairMedicamentoCursor(Cursor cursor) {
        Medicamento med = new Medicamento();
        med.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_MED_ID)));
        med.setIdIdoso(cursor.getInt(cursor.getColumnIndexOrThrow(COL_MED_ID_IDOSO)));
        med.setNomeMedicamento(cursor.getString(cursor.getColumnIndexOrThrow(COL_MED_NOME)));
        med.setDosagem(cursor.getString(cursor.getColumnIndexOrThrow(COL_MED_DOSAGEM)));
        
        int colEstoqueIdx = cursor.getColumnIndex(COL_MED_ESTOQUE);
        if (colEstoqueIdx != -1) {
            med.setEstoque(cursor.getString(colEstoqueIdx));
        }

        int colTipoIdx = cursor.getColumnIndex(COL_MED_TIPO);
        if (colTipoIdx != -1) {
            med.setTipo(cursor.getString(colTipoIdx));
        }
        
        med.setHorarioInicial(cursor.getString(cursor.getColumnIndexOrThrow(COL_MED_HORARIO_INICIAL)));
        med.setFrequenciaHoras(cursor.getInt(cursor.getColumnIndexOrThrow(COL_MED_FREQUENCIA_HORAS)));
        
        int colObsIdx = cursor.getColumnIndex(COL_MED_OBSERVACAO);
        if (colObsIdx != -1) {
            med.setObservacaoMedica(cursor.getString(colObsIdx));
        }

        med.setStatusAtivo(cursor.getInt(cursor.getColumnIndexOrThrow(COL_MED_STATUS_ATIVO)));
        
        int colNomeIdoso = cursor.getColumnIndex("nome_idoso");
        if (colNomeIdoso != -1) {
            med.setNomeIdoso(cursor.getString(colNomeIdoso));
        }
        return med;
    }

    public int excluirMedicamento(int idMedicamento) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_MEDICAMENTO, COL_MED_ID + " = ?", new String[]{String.valueOf(idMedicamento)});
    }

    /**
     * Desconta 1 unidade do estoque textual de um medicamento quando uma dose é administrada,
     * preservando a unidade descritiva (ex: "10 comprimidos" -> "9 comprimidos").
     */
    public void decrementarEstoqueUmaUnidade(int idMedicamento) {
        if (idMedicamento <= 0) {
            return;
        }
        Medicamento med = buscarMedicamentoPorId(idMedicamento);
        if (med == null || med.getEstoque() == null || med.getEstoque().trim().isEmpty()) {
            return;
        }
        String novoEstoque = com.example.util.EstoqueAlertaManager.decrementarUmaUnidadeTexto(med.getEstoque());
        if (!novoEstoque.equals(med.getEstoque())) {
            SQLiteDatabase db = obterBancoComEsquemaGarantido();
            ContentValues cv = new ContentValues();
            cv.put(COL_MED_ESTOQUE, novoEstoque);
            db.update(TABLE_MEDICAMENTO, cv, COL_MED_ID + " = ?", new String[]{String.valueOf(idMedicamento)});
        }
    }
}
