package com.example.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.model.PassagemTurno;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * DAO responsável pelo CRUD de Passagens de Turno e Observações no SQLite local.
 */
public class PassagemTurnoDAO {

    private final DatabaseHelper dbHelper;

    public PassagemTurnoDAO(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long inserir(PassagemTurno pt) {
        if (pt == null) return -1;
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        if (pt.getTimestampRegistro() <= 0) {
            pt.setTimestampRegistro(System.currentTimeMillis());
        }
        if (pt.getDataHoraRegistro().isEmpty()) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            pt.setDataHoraRegistro(sdf.format(new Date(pt.getTimestampRegistro())));
        }

        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_PT_TURNO, pt.getTurno());
        cv.put(DatabaseHelper.COL_PT_DATA_HORA, pt.getDataHoraRegistro());
        cv.put(DatabaseHelper.COL_PT_TIMESTAMP, pt.getTimestampRegistro());
        cv.put(DatabaseHelper.COL_PT_PROFISSIONAL_ORIGEM, pt.getProfissionalOrigem());
        cv.put(DatabaseHelper.COL_PT_CARGO_ORIGEM, pt.getCargoOrigem());
        cv.put(DatabaseHelper.COL_PT_ID_IDOSO, pt.getIdIdoso());
        cv.put(DatabaseHelper.COL_PT_NOME_IDOSO, pt.getNomeIdoso());
        cv.put(DatabaseHelper.COL_PT_PRIORIDADE, pt.getPrioridade());
        cv.put(DatabaseHelper.COL_PT_OBSERVACOES, pt.getObservacoes());
        cv.put(DatabaseHelper.COL_PT_INTERCORRENCIAS, pt.getIntercorrencias());
        cv.put(DatabaseHelper.COL_PT_PENDENCIAS, pt.getPendenciasProximoTurno());
        cv.put(DatabaseHelper.COL_PT_CONFERIDO, pt.isConferido() ? 1 : 0);
        cv.put(DatabaseHelper.COL_PT_PROFISSIONAL_DESTINO, pt.getProfissionalDestino());
        cv.put(DatabaseHelper.COL_PT_DATA_HORA_CONFERENCIA, pt.getDataHoraConferencia());

        long id = db.insert(DatabaseHelper.TABLE_PASSAGEM_TURNO, null, cv);
        if (id != -1) {
            pt.setId((int) id);
        }
        return id;
    }

    public List<PassagemTurno> listarTodas() {
        List<PassagemTurno> lista = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT * FROM " + DatabaseHelper.TABLE_PASSAGEM_TURNO
                + " ORDER BY " + DatabaseHelper.COL_PT_CONFERIDO + " ASC, "
                + "CASE " + DatabaseHelper.COL_PT_PRIORIDADE
                + " WHEN 'URGENTE' THEN 0 WHEN 'ATENCAO' THEN 1 ELSE 2 END ASC, "
                + DatabaseHelper.COL_PT_TIMESTAMP + " DESC, "
                + DatabaseHelper.COL_PT_ID + " DESC";

        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    lista.add(extrairCursor(cursor));
                } while (cursor.moveToNext());
            }
        }
        return lista;
    }

    public int confirmarRecebimento(int idPassagem, String profissionalDestino) {
        if (idPassagem <= 0) return 0;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        String agora = sdf.format(new Date());

        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_PT_CONFERIDO, 1);
        cv.put(DatabaseHelper.COL_PT_PROFISSIONAL_DESTINO,
                profissionalDestino != null && !profissionalDestino.trim().isEmpty()
                        ? profissionalDestino.trim() : "Equipe de Plantão");
        cv.put(DatabaseHelper.COL_PT_DATA_HORA_CONFERENCIA, agora);

        return db.update(
                DatabaseHelper.TABLE_PASSAGEM_TURNO,
                cv,
                DatabaseHelper.COL_PT_ID + " = ?",
                new String[]{String.valueOf(idPassagem)}
        );
    }

    public int excluir(int idPassagem) {
        if (idPassagem <= 0) return 0;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(
                DatabaseHelper.TABLE_PASSAGEM_TURNO,
                DatabaseHelper.COL_PT_ID + " = ?",
                new String[]{String.valueOf(idPassagem)}
        );
    }

    public int contarPendentesConferencia() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_PASSAGEM_TURNO
                + " WHERE " + DatabaseHelper.COL_PT_CONFERIDO + " = 0";
        try (Cursor c = db.rawQuery(sql, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getInt(0);
            }
        }
        return 0;
    }

    public int contarUrgentesPendentes() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_PASSAGEM_TURNO
                + " WHERE " + DatabaseHelper.COL_PT_CONFERIDO + " = 0"
                + " AND " + DatabaseHelper.COL_PT_PRIORIDADE + " IN ('URGENTE', 'ATENCAO')";
        try (Cursor c = db.rawQuery(sql, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getInt(0);
            }
        }
        return 0;
    }

    private PassagemTurno extrairCursor(Cursor cursor) {
        PassagemTurno pt = new PassagemTurno();
        pt.setId(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_ID)));
        pt.setTurno(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_TURNO)));
        pt.setDataHoraRegistro(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_DATA_HORA)));
        pt.setTimestampRegistro(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_TIMESTAMP)));
        pt.setProfissionalOrigem(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_PROFISSIONAL_ORIGEM)));
        pt.setCargoOrigem(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_CARGO_ORIGEM)));
        pt.setIdIdoso(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_ID_IDOSO)));
        pt.setNomeIdoso(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_NOME_IDOSO)));
        pt.setPrioridade(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_PRIORIDADE)));
        pt.setObservacoes(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_OBSERVACOES)));
        pt.setIntercorrencias(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_INTERCORRENCIAS)));
        pt.setPendenciasProximoTurno(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_PENDENCIAS)));
        pt.setConferido(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_CONFERIDO)) == 1);
        pt.setProfissionalDestino(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_PROFISSIONAL_DESTINO)));
        pt.setDataHoraConferencia(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PT_DATA_HORA_CONFERENCIA)));
        return pt;
    }
}
