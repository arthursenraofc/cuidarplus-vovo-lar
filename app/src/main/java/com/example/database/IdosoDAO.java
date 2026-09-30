package com.example.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.model.Idoso;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe DAO (Data Access Object) responsável pelas operações de CRUD na tabela de idosos.
 * Utiliza try-with-resources para fechamento automático de cursores e conexões com o SQLite,
 * prevenindo vazamentos de memória (resource leaks).
 */
public class IdosoDAO {

    private final DatabaseHelper dbHelper;

    public IdosoDAO(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    /**
     * Insere um novo Idoso no banco de dados SQLite.
     * @param idoso Objeto idoso com os dados a serem salvos
     * @return ID do registro inserido ou -1 em caso de erro
     */
    public long inserir(Idoso idoso) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_NOME, idoso.getNome());
        cv.put(DatabaseHelper.COL_DATA_NASCIMENTO, idoso.getDataNascimento());
        cv.put(DatabaseHelper.COL_TIPO_SANGUINEO, idoso.getTipoSanguineo());
        cv.put(DatabaseHelper.COL_ALERGIAS, idoso.getAlergias());
        cv.put(DatabaseHelper.COL_RESTRICOES_MEDICAS, idoso.getRestricoesMedicas());
        cv.put(DatabaseHelper.COL_CONTATO_EMERGENCIA, idoso.getContatoEmergencia());
        cv.put(DatabaseHelper.COL_CAMINHO_FOTO, idoso.getCaminhoFoto());

        return db.insert(DatabaseHelper.TABLE_IDOSOS, null, cv);
    }

    /**
     * Atualiza os dados de um Idoso existente.
     * @param idoso Objeto com o ID e novos dados
     * @return Quantidade de linhas afetadas
     */
    public int atualizar(Idoso idoso) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_NOME, idoso.getNome());
        cv.put(DatabaseHelper.COL_DATA_NASCIMENTO, idoso.getDataNascimento());
        cv.put(DatabaseHelper.COL_TIPO_SANGUINEO, idoso.getTipoSanguineo());
        cv.put(DatabaseHelper.COL_ALERGIAS, idoso.getAlergias());
        cv.put(DatabaseHelper.COL_RESTRICOES_MEDICAS, idoso.getRestricoesMedicas());
        cv.put(DatabaseHelper.COL_CONTATO_EMERGENCIA, idoso.getContatoEmergencia());
        cv.put(DatabaseHelper.COL_CAMINHO_FOTO, idoso.getCaminhoFoto());

        return db.update(
                DatabaseHelper.TABLE_IDOSOS,
                cv,
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(idoso.getId())}
        );
    }

    /**
     * Remove o registro de um Idoso pelo seu ID (long).
     * @param id Identificador do idoso
     * @return true se removido com sucesso
     */
    public boolean excluir(long id) {
        return excluir((int) id) > 0;
    }

    /**
     * Remove o registro de um Idoso pelo seu ID (int).
     */
    public int excluir(int id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(
                DatabaseHelper.TABLE_IDOSOS,
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(id)}
        );
    }

    /**
     * Busca um único idoso pelo ID (long).
     * @param id Identificador
     * @return Objeto Idoso encontrado ou null
     */
    public Idoso buscarPorId(long id) {
        return buscarPorId((int) id);
    }

    /**
     * Busca um único idoso pelo ID (int).
     * @param id Identificador
     * @return Objeto Idoso encontrado ou null
     */
    public Idoso buscarPorId(int id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                     DatabaseHelper.TABLE_IDOSOS,
                     null,
                     DatabaseHelper.COL_ID + " = ?",
                     new String[]{String.valueOf(id)},
                     null,
                     null,
                     null
             )) {

            if (cursor != null && cursor.moveToFirst()) {
                return converterCursorParaIdoso(cursor);
            }
        }
        return null;
    }

    /**
     * Retorna a quantidade total de idosos cadastrados.
     */
    public int contarTotalIdosos() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_IDOSOS, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    /**
     * Retorna a lista de todos os idosos cadastrados ordenados alfabeticamente por nome.
     */
    public List<Idoso> listarTodos() {
        List<Idoso> lista = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                     DatabaseHelper.TABLE_IDOSOS,
                     null,
                     null,
                     null,
                     null,
                     null,
                     DatabaseHelper.COL_NOME + " ASC"
             )) {

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    lista.add(converterCursorParaIdoso(cursor));
                } while (cursor.moveToNext());
            }
        }
        return lista;
    }

    /**
     * Filtra idosos por termo no nome ou nas restrições médicas.
     * @param termo Palavra-chave para busca
     * @return Lista de idosos correspondentes
     */
    public List<Idoso> buscarPorTermo(String termo) {
        if (termo == null || termo.trim().isEmpty()) {
            return listarTodos();
        }

        List<Idoso> lista = new ArrayList<>();
        String filtro = "%" + termo.trim() + "%";
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        try (Cursor cursor = db.query(
                     DatabaseHelper.TABLE_IDOSOS,
                     null,
                     DatabaseHelper.COL_NOME + " LIKE ? OR " + DatabaseHelper.COL_RESTRICOES_MEDICAS + " LIKE ? OR " + DatabaseHelper.COL_ALERGIAS + " LIKE ?",
                     new String[]{filtro, filtro, filtro},
                     null,
                     null,
                     DatabaseHelper.COL_NOME + " ASC"
             )) {

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    lista.add(converterCursorParaIdoso(cursor));
                } while (cursor.moveToNext());
            }
        }
        return lista;
    }

    /**
     * Método utilitário para converter uma linha do Cursor em um objeto Idoso.
     */
    private Idoso converterCursorParaIdoso(Cursor cursor) {
        int id = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
        String nome = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NOME));
        String dataNasc = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DATA_NASCIMENTO));
        String tipoSangue = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TIPO_SANGUINEO));
        String alergias = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ALERGIAS));
        String restricoes = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RESTRICOES_MEDICAS));
        String contatoEmergencia = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CONTATO_EMERGENCIA));
        String caminhoFoto = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAMINHO_FOTO));

        return new Idoso(id, nome, dataNasc, tipoSangue, alergias, restricoes, contatoEmergencia, caminhoFoto);
    }
}
