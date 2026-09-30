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
    private static final String DATABASE_NAME = "lar_idosos.db";
    public static final int DATABASE_VERSION = 2;

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

    // Tabela Medicamentos (Sprint 2)
    public static final String TABLE_MEDICAMENTO = "medicamento";
    public static final String COL_MED_ID = "id";
    public static final String COL_MED_ID_IDOSO = "id_idoso";
    public static final String COL_MED_NOME = "nome_medicamento";
    public static final String COL_MED_DOSAGEM = "dosagem";
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
    private static final String CREATE_TABLE_IDOSOS = "CREATE TABLE " + TABLE_IDOSOS + " ("
            + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_NOME + " TEXT NOT NULL, "
            + COL_DATA_NASCIMENTO + " TEXT, "
            + COL_TIPO_SANGUINEO + " TEXT, "
            + COL_ALERGIAS + " TEXT, "
            + COL_RESTRICOES_MEDICAS + " TEXT, "
            + COL_CONTATO_EMERGENCIA + " TEXT, "
            + COL_CAMINHO_FOTO + " TEXT);";

    // SQL de Criação da Tabela Medicamento
    private static final String CREATE_TABLE_MEDICAMENTO = "CREATE TABLE " + TABLE_MEDICAMENTO + " ("
            + COL_MED_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_MED_ID_IDOSO + " INTEGER NOT NULL, "
            + COL_MED_NOME + " TEXT NOT NULL, "
            + COL_MED_DOSAGEM + " TEXT NOT NULL, "
            + COL_MED_TIPO + " TEXT NOT NULL DEFAULT 'Comprimido', "
            + COL_MED_HORARIO_INICIAL + " TEXT NOT NULL, "
            + COL_MED_FREQUENCIA_HORAS + " INTEGER NOT NULL, "
            + COL_MED_OBSERVACAO + " TEXT, "
            + COL_MED_STATUS_ATIVO + " INTEGER NOT NULL DEFAULT 1, "
            + "FOREIGN KEY(" + COL_MED_ID_IDOSO + ") REFERENCES " + TABLE_IDOSOS + "(" + COL_ID + ") ON DELETE CASCADE);";

    // SQL de Criação da Tabela Histórico de Medicação
    private static final String CREATE_TABLE_HISTORICO_MEDICACAO = "CREATE TABLE " + TABLE_HISTORICO_MEDICACAO + " ("
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

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    public DatabaseHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Upgrade estrutural seguro
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORICO_MEDICACAO);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEDICAMENTO);
            db.execSQL(CREATE_TABLE_MEDICAMENTO);
            db.execSQL(CREATE_TABLE_HISTORICO_MEDICACAO);
            inserirMedicamentosIniciais(db);
        }
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
        db.insert(TABLE_IDOSOS, null, cv1);

        ContentValues cv2 = new ContentValues();
        cv2.put(COL_NOME, "Seu Antônio Carlos Ribeiro");
        cv2.put(COL_DATA_NASCIMENTO, "08/11/1939");
        cv2.put(COL_TIPO_SANGUINEO, "A+");
        cv2.put(COL_ALERGIAS, "Nenhuma alergia conhecida");
        cv2.put(COL_RESTRICOES_MEDICAS, "Mal de Parkinson inicial. Risco moderado de quedas. Uso de andador.");
        cv2.put(COL_CONTATO_EMERGENCIA, "Filho Roberto - (51) 99123-8877");
        cv2.put(COL_CAMINHO_FOTO, "");
        db.insert(TABLE_IDOSOS, null, cv2);

        ContentValues cv3 = new ContentValues();
        cv3.put(COL_NOME, "Dona Tereza Cristina Moreira");
        cv3.put(COL_DATA_NASCIMENTO, "22/07/1942");
        cv3.put(COL_TIPO_SANGUINEO, "B-");
        cv3.put(COL_ALERGIAS, "Intolerância severa à lactose e frutos do mar");
        cv3.put(COL_RESTRICOES_MEDICAS, "Osteoporose avançada. Fisioterapia motora 3x por semana.");
        cv3.put(COL_CONTATO_EMERGENCIA, "Irmã Helena - (51) 98844-3322");
        cv3.put(COL_CAMINHO_FOTO, "");
        db.insert(TABLE_IDOSOS, null, cv3);

        inserirMedicamentosIniciais(db);
    }

    private void inserirMedicamentosIniciais(SQLiteDatabase db) {
        ContentValues m1 = new ContentValues();
        m1.put(COL_MED_ID_IDOSO, 1);
        m1.put(COL_MED_NOME, "Losartana Potássica");
        m1.put(COL_MED_DOSAGEM, "50mg (1 comprimido)");
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
     * Lista todos os medicamentos de um idoso.
     */
    public List<Medicamento> listarMedicamentosPorIdoso(int idIdoso) {
        List<Medicamento> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String sql = "SELECT m." + COL_MED_ID + ", m." + COL_MED_ID_IDOSO + ", m." + COL_MED_NOME + ", "
                + "m." + COL_MED_DOSAGEM + ", m." + COL_MED_TIPO + ", m." + COL_MED_HORARIO_INICIAL + ", "
                + "m." + COL_MED_FREQUENCIA_HORAS + ", m." + COL_MED_OBSERVACAO + ", "
                + "m." + COL_MED_STATUS_ATIVO + ", i." + COL_NOME + " AS nome_idoso "
                + "FROM " + TABLE_MEDICAMENTO + " m "
                + "INNER JOIN " + TABLE_IDOSOS + " i ON m." + COL_MED_ID_IDOSO + " = i." + COL_ID + " "
                + "WHERE m." + COL_MED_ID_IDOSO + " = ? "
                + "ORDER BY m." + COL_MED_HORARIO_INICIAL + " ASC";

        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(idIdoso)})) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Medicamento med = extrairMedicamentoCursor(cursor);
                    lista.add(med);
                } while (cursor.moveToNext());
            }
        }
        return lista;
    }

    /**
     * Busca um medicamento por seu ID com dados completos.
     */
    public Medicamento buscarMedicamentoPorId(int idMedicamento) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT m." + COL_MED_ID + ", m." + COL_MED_ID_IDOSO + ", m." + COL_MED_NOME + ", "
                + "m." + COL_MED_DOSAGEM + ", m." + COL_MED_TIPO + ", m." + COL_MED_HORARIO_INICIAL + ", "
                + "m." + COL_MED_FREQUENCIA_HORAS + ", m." + COL_MED_OBSERVACAO + ", "
                + "m." + COL_MED_STATUS_ATIVO + ", i." + COL_NOME + " AS nome_idoso "
                + "FROM " + TABLE_MEDICAMENTO + " m "
                + "INNER JOIN " + TABLE_IDOSOS + " i ON m." + COL_MED_ID_IDOSO + " = i." + COL_ID + " "
                + "WHERE m." + COL_MED_ID + " = ?";

        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(idMedicamento)})) {
            if (cursor != null && cursor.moveToFirst()) {
                return extrairMedicamentoCursor(cursor);
            }
        }
        return null;
    }

    /**
     * Busca todos os medicamentos ativos cadastrados no sistema.
     */
    public List<Medicamento> buscarMedicamentosAgendados() {
        List<Medicamento> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String sql = "SELECT m." + COL_MED_ID + ", m." + COL_MED_ID_IDOSO + ", m." + COL_MED_NOME + ", "
                + "m." + COL_MED_DOSAGEM + ", m." + COL_MED_TIPO + ", m." + COL_MED_HORARIO_INICIAL + ", "
                + "m." + COL_MED_FREQUENCIA_HORAS + ", m." + COL_MED_OBSERVACAO + ", "
                + "m." + COL_MED_STATUS_ATIVO + ", i." + COL_NOME + " AS nome_idoso "
                + "FROM " + TABLE_MEDICAMENTO + " m "
                + "INNER JOIN " + TABLE_IDOSOS + " i ON m." + COL_MED_ID_IDOSO + " = i." + COL_ID + " "
                + "WHERE m." + COL_MED_STATUS_ATIVO + " = 1 "
                + "ORDER BY m." + COL_MED_HORARIO_INICIAL + " ASC";

        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    lista.add(extrairMedicamentoCursor(cursor));
                } while (cursor.moveToNext());
            }
        }
        return lista;
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
}
