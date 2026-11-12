package com.example.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.model.Idoso;
import com.example.model.PerfilUsuario;
import com.example.util.PermissaoUtils;
import com.example.util.SessaoManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe DAO (Data Access Object) responsável pelas operações de CRUD na tabela de idosos.
 * Contém travas de segurança de nível de persistência (RBAC) para garantir que
 * operações de alteração e exclusão somente sejam executadas por perfis autorizados (ADMINISTRADOR).
 */
public class IdosoDAO {

    private static final String TAG = "IdosoDAO";
    private final DatabaseHelper dbHelper;
    private final Context context;

    public IdosoDAO(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    /**
     * Insere um novo Idoso validando a permissão do perfil ativo na sessão.
     */
    public long inserir(Idoso idoso) {
        PerfilUsuario perfilAtivo = SessaoManager.getPerfil(context);
        return inserir(idoso, perfilAtivo);
    }

    /**
     * Insere um novo Idoso com trava de segurança explícita para o perfil informado.
     * @throws AcessoNaoAutorizadoException se o perfil não tiver permissão de cadastro
     */
    public long inserir(Idoso idoso, PerfilUsuario perfil) {
        if (!PermissaoUtils.podeCadastrarIdoso(perfil)) {
            Log.e(TAG, "Tentativa não autorizada de cadastro de idoso por: " + perfil);
            throw new AcessoNaoAutorizadoException(perfil, "Cadastrar Idoso");
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_NOME, idoso.getNome());
        cv.put(DatabaseHelper.COL_DATA_NASCIMENTO, idoso.getDataNascimento());
        cv.put(DatabaseHelper.COL_TIPO_SANGUINEO, idoso.getTipoSanguineo());
        cv.put(DatabaseHelper.COL_ALERGIAS, idoso.getAlergias());
        cv.put(DatabaseHelper.COL_RESTRICOES_MEDICAS, idoso.getRestricoesMedicas());
        cv.put(DatabaseHelper.COL_CONTATO_EMERGENCIA, idoso.getContatoEmergencia());
        cv.put(DatabaseHelper.COL_CAMINHO_FOTO, idoso.getCaminhoFoto());
        if (idoso.getCpf() != null) cv.put(DatabaseHelper.COL_CPF, idoso.getCpf());
        if (idoso.getLeitoQuarto() != null) cv.put(DatabaseHelper.COL_LEITO_QUARTO, idoso.getLeitoQuarto());
        if (idoso.getNomeFamiliar() != null) cv.put(DatabaseHelper.COL_NOME_FAMILIAR, idoso.getNomeFamiliar());
        if (idoso.getGrauParentesco() != null) cv.put(DatabaseHelper.COL_GRAU_PARENTESCO, idoso.getGrauParentesco());

        return db.insert(DatabaseHelper.TABLE_IDOSOS, null, cv);
    }

    /**
     * Atualiza os dados de um Idoso existente validando o perfil ativo na sessão.
     */
    public int atualizar(Idoso idoso) {
        PerfilUsuario perfilAtivo = SessaoManager.getPerfil(context);
        return atualizar(idoso, perfilAtivo);
    }

    /**
     * Atualiza os dados de um Idoso existente com validação de segurança RBAC.
     * @throws AcessoNaoAutorizadoException se o perfil não for Administrador
     */
    public int atualizar(Idoso idoso, PerfilUsuario perfil) {
        if (!PermissaoUtils.podeEditarIdoso(perfil)) {
            Log.e(TAG, "Tentativa não autorizada de atualização de idoso por: " + perfil);
            throw new AcessoNaoAutorizadoException(perfil, "Editar/Atualizar Idoso");
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_NOME, idoso.getNome());
        cv.put(DatabaseHelper.COL_DATA_NASCIMENTO, idoso.getDataNascimento());
        cv.put(DatabaseHelper.COL_TIPO_SANGUINEO, idoso.getTipoSanguineo());
        cv.put(DatabaseHelper.COL_ALERGIAS, idoso.getAlergias());
        cv.put(DatabaseHelper.COL_RESTRICOES_MEDICAS, idoso.getRestricoesMedicas());
        cv.put(DatabaseHelper.COL_CONTATO_EMERGENCIA, idoso.getContatoEmergencia());
        cv.put(DatabaseHelper.COL_CAMINHO_FOTO, idoso.getCaminhoFoto());
        if (idoso.getCpf() != null) cv.put(DatabaseHelper.COL_CPF, idoso.getCpf());
        if (idoso.getLeitoQuarto() != null) cv.put(DatabaseHelper.COL_LEITO_QUARTO, idoso.getLeitoQuarto());
        if (idoso.getNomeFamiliar() != null) cv.put(DatabaseHelper.COL_NOME_FAMILIAR, idoso.getNomeFamiliar());
        if (idoso.getGrauParentesco() != null) cv.put(DatabaseHelper.COL_GRAU_PARENTESCO, idoso.getGrauParentesco());

        return db.update(
                DatabaseHelper.TABLE_IDOSOS,
                cv,
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(idoso.getId())}
        );
    }

    /**
     * Atualiza exclusivamente o caminho local da foto de perfil de um residente no SQLite.
     */
    public int atualizarFotoPerfil(int idIdoso, String novoCaminhoFoto) {
        PerfilUsuario perfilAtivo = SessaoManager.getPerfil(context);
        return atualizarFotoPerfil(idIdoso, novoCaminhoFoto, perfilAtivo);
    }

    /**
     * Atualiza exclusivamente o caminho local da foto de perfil validando o perfil ativo.
     */
    public int atualizarFotoPerfil(int idIdoso, String novoCaminhoFoto, PerfilUsuario perfil) {
        if (!PermissaoUtils.podeAtualizarFotoPerfil(perfil)) {
            Log.w(TAG, "Tentativa não autorizada de atualização de foto de perfil por: " + perfil);
            throw new AcessoNaoAutorizadoException(perfil, "Atualizar Foto de Perfil do Residente");
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_CAMINHO_FOTO, novoCaminhoFoto != null ? novoCaminhoFoto : "");

        return db.update(
                DatabaseHelper.TABLE_IDOSOS,
                cv,
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(idIdoso)}
        );
    }

    /**
     * Remove o registro de um Idoso pelo seu ID (long) com perfil da sessão.
     */
    public boolean excluir(long id) {
        PerfilUsuario perfilAtivo = SessaoManager.getPerfil(context);
        return excluir(id, perfilAtivo);
    }

    /**
     * Remove o registro de um Idoso pelo seu ID (long) com perfil explícito.
     */
    public boolean excluir(long id, PerfilUsuario perfil) {
        return excluir((int) id, perfil) > 0;
    }

    /**
     * Remove o registro de um Idoso pelo seu ID (int) com perfil da sessão.
     */
    public int excluir(int id) {
        PerfilUsuario perfilAtivo = SessaoManager.getPerfil(context);
        return excluir(id, perfilAtivo);
    }

    /**
     * Remove o registro de um Idoso pelo seu ID (int) com validação de segurança RBAC.
     * @throws AcessoNaoAutorizadoException se o perfil não for Administrador
     */
    public int excluir(int id, PerfilUsuario perfil) {
        if (!PermissaoUtils.podeExcluirIdoso(perfil)) {
            Log.e(TAG, "Tentativa não autorizada de exclusão de idoso por: " + perfil);
            throw new AcessoNaoAutorizadoException(perfil, "Excluir Idoso");
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(
                DatabaseHelper.TABLE_IDOSOS,
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(id)}
        );
    }

    /**
     * Busca um único idoso pelo ID (long).
     */
    public Idoso buscarPorId(long id) {
        return buscarPorId((int) id);
    }

    /**
     * Busca um único idoso pelo ID (int).
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
     * Retorna os residentes aniversariantes do mês corrente ordenados pelo dia do aniversário.
     */
    public List<com.example.util.DashboardManager.AniversarianteMes> listarAniversariantesDoMesAtual() {
        return com.example.util.DashboardManager.filtrarAniversariantesDoMesAtual(listarTodos());
    }

    /**
     * Retorna os residentes aniversariantes para um mês específico (1 a 12).
     */
    public List<com.example.util.DashboardManager.AniversarianteMes> listarAniversariantesDoMes(int mes1a12, int anoReferencia) {
        return com.example.util.DashboardManager.filtrarAniversariantesDoMes(listarTodos(), -1, mes1a12, anoReferencia);
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

        Idoso idoso = new Idoso(id, nome, dataNasc, tipoSangue, alergias, restricoes, contatoEmergencia, caminhoFoto);

        int idxCpf = cursor.getColumnIndex(DatabaseHelper.COL_CPF);
        if (idxCpf != -1) idoso.setCpf(cursor.getString(idxCpf));

        int idxLeito = cursor.getColumnIndex(DatabaseHelper.COL_LEITO_QUARTO);
        if (idxLeito != -1) idoso.setLeitoQuarto(cursor.getString(idxLeito));

        int idxNomeFam = cursor.getColumnIndex(DatabaseHelper.COL_NOME_FAMILIAR);
        if (idxNomeFam != -1) idoso.setNomeFamiliar(cursor.getString(idxNomeFam));

        int idxParentesco = cursor.getColumnIndex(DatabaseHelper.COL_GRAU_PARENTESCO);
        if (idxParentesco != -1) idoso.setGrauParentesco(cursor.getString(idxParentesco));

        return idoso;
    }
}
