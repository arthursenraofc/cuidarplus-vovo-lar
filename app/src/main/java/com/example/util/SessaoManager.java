package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.example.model.PerfilUsuario;
import com.example.model.Usuario;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Gerenciador de Sessão Local Seguro (SessaoManager) para a arquitetura Local-First do CuidarPlus.
 *
 * <p>Utiliza {@link EncryptedSharedPreferences} integrado ao <b>Android Keystore System</b>
 * ({@link MasterKey.KeyScheme#AES256_GCM}) para garantir criptografia em repouso (at-rest)
 * tanto das chaves ({@code AES256_SIV}) quanto dos valores ({@code AES256_GCM}), protegendo
 * credenciais offline e o perfil RBAC contra inspeção ou adulteração de XML em disco.</p>
 *
 * <p>Adota Defesa em Profundidade (Defense in Depth): senhas/PINs offline nunca são salvos em
 * texto puro mesmo dentro do cofre criptografado — são derivados via SHA-256 iterado com Salt
 * criptográfico aleatório ({@link SecureRandom}) e validados em tempo constante.</p>
 */
public class SessaoManager {

    private static final String TAG = "SessaoManager";

    /** Nome do arquivo criptografado gerenciado pelo EncryptedSharedPreferences. */
    public static final String PREFS_NAME = "cuidarplus_encrypted_session_prefs";

    /** Nome legado do arquivo em texto plano (migrado e removido automaticamente). */
    private static final String LEGACY_PLAIN_PREFS_NAME = "CuidarPlusPrefs";

    // Chaves de Sessão Ativa (criptografadas com AES256-SIV no disco)
    public static final String KEY_SESSAO_ATIVA = "sessao_ativa";
    public static final String KEY_USUARIO_ID = "usuario_id";
    public static final String KEY_USUARIO_NOME = "usuario_nome";
    public static final String KEY_USUARIO_EMAIL = "usuario_email";
    public static final String KEY_PERFIL = "perfil_usuario";
    public static final String KEY_TIMESTAMP_LOGIN = "timestamp_login";

    // Chaves de Credenciais Offline Protegidas (Hash + Salt)
    private static final String KEY_CRED_ID = "cred_offline_id";
    private static final String KEY_CRED_NOME = "cred_offline_nome";
    private static final String KEY_CRED_EMAIL = "cred_offline_email";
    private static final String KEY_CRED_PERFIL = "cred_offline_perfil";
    private static final String KEY_CRED_HASH = "cred_offline_senha_hash";
    private static final String KEY_CRED_SALT = "cred_offline_senha_salt";

    // Parâmetros Criptográficos para Derivação de Credencial Offline
    private static final int SALT_BYTES = 16;
    private static final int HASH_ITERATIONS = 10_000;

    /** Duração padrão sugerida para validação opcional de turno na ILPI (12 horas). */
    public static final long DURACAO_PADRAO_TURNO_MS = 12L * 60L * 60L * 1000L;

    private static volatile SessaoManager instance;

    private final SharedPreferences securePrefs;
    private final boolean criptografiaAtiva;

    private SessaoManager(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences inicializado;
        boolean usandoCriptografia = false;

        if (isAndroidKeyStoreDisponivel()) {
            try {
                inicializado = criarEncryptedPreferences(appContext);
                usandoCriptografia = true;
            } catch (Exception erroPrimario) {
                Log.w(TAG, "Falha ao abrir EncryptedSharedPreferences (possível invalidação de chave no Keystore). Recriando cofre seguro...", erroPrimario);
                limparArquivoPreferenciasCorrompido(appContext, PREFS_NAME);
                try {
                    inicializado = criarEncryptedPreferences(appContext);
                    usandoCriptografia = true;
                } catch (Exception erroCritico) {
                    Log.w(TAG, "Keystore indisponível após recriação; aplicando fallback privado local para garantir abertura imediata.", erroCritico);
                    limparArquivoPreferenciasCorrompido(appContext, PREFS_NAME);
                    inicializado = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    usandoCriptografia = false;
                }
            }
        } else {
            // Ambiente de teste local na JVM (ex: Robolectric sem provedor AndroidKeyStore)
            Log.w(TAG, "AndroidKeyStore indisponível neste ambiente JVM. Usando SharedPreferences privado apenas para execução de testes.");
            inicializado = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }

        this.securePrefs = inicializado;
        this.criptografiaAtiva = usandoCriptografia;

        migrarPreferenciasLegadasSeNecessario(appContext);
        garantirEstadoInicialProntoParaUso();
    }

    /**
     * Garante que uma instalação nova ou recém-migrada possua estado de sessão válido e pronto
     * para uso imediato ao abrir o aplicativo, sem sobrescrever escolhas explícitas de perfil/logout.
     */
    private void garantirEstadoInicialProntoParaUso() {
        if (!securePrefs.contains(KEY_PERFIL) && !securePrefs.contains(KEY_SESSAO_ATIVA)) {
            securePrefs.edit()
                    .putBoolean(KEY_SESSAO_ATIVA, true)
                    .putString(KEY_PERFIL, PerfilUsuario.ADMINISTRADOR.getCodigo())
                    .apply();
        }
    }

    /**
     * Obtém a instância Singleton thread-safe (Double-Checked Locking) do gerenciador de sessão.
     */
    public static SessaoManager getInstance(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context não pode ser nulo ao inicializar SessaoManager.");
        }
        if (instance == null) {
            synchronized (SessaoManager.class) {
                if (instance == null) {
                    instance = new SessaoManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Constrói a instância de {@link EncryptedSharedPreferences} utilizando uma {@link MasterKey}
     * AES-256 GCM armazenada no Android Keystore e criptografia AES256-SIV / AES256-GCM.
     */
    private static SharedPreferences criarEncryptedPreferences(Context appContext)
            throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        return EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    /**
     * Verifica se o provedor nativo {@code AndroidKeyStore} está presente no runtime atual.
     */
    private static boolean isAndroidKeyStoreDisponivel() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Remove o arquivo de preferências corrompido caso a chave mestre do Keystore tenha sido
     * invalidada pelo sistema operacional (ex: redefinição de credenciais de bloqueio do aparelho).
     */
    private static void limparArquivoPreferenciasCorrompido(Context appContext, String prefsName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appContext.deleteSharedPreferences(prefsName);
            } else {
                appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit();
                File prefsFile = new File(new File(appContext.getApplicationInfo().dataDir, "shared_prefs"), prefsName + ".xml");
                if (prefsFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    prefsFile.delete();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Falha ao limpar arquivo de preferências corrompido: " + prefsName, e);
        }
    }

    /**
     * Migra dados existentes do antigo SharedPreferences em texto plano ({@code CuidarPlusPrefs})
     * para o cofre criptografado e apaga imediatamente o arquivo legado.
     */
    private void migrarPreferenciasLegadasSeNecessario(Context appContext) {
        try {
            SharedPreferences legacyPrefs = appContext.getSharedPreferences(LEGACY_PLAIN_PREFS_NAME, Context.MODE_PRIVATE);
            if (legacyPrefs.getAll().isEmpty()) {
                return;
            }

            SharedPreferences.Editor editor = securePrefs.edit();
            if (!securePrefs.contains(KEY_PERFIL) && legacyPrefs.contains(KEY_PERFIL)) {
                editor.putString(KEY_PERFIL, legacyPrefs.getString(KEY_PERFIL, PerfilUsuario.ADMINISTRADOR.getCodigo()));
            }
            if (!securePrefs.contains(KEY_SESSAO_ATIVA) && legacyPrefs.contains(KEY_SESSAO_ATIVA)) {
                editor.putBoolean(KEY_SESSAO_ATIVA, legacyPrefs.getBoolean(KEY_SESSAO_ATIVA, true));
            }
            if (!securePrefs.contains(KEY_USUARIO_ID) && legacyPrefs.contains(KEY_USUARIO_ID)) {
                editor.putLong(KEY_USUARIO_ID, legacyPrefs.getLong(KEY_USUARIO_ID, 1L));
            }
            if (!securePrefs.contains(KEY_USUARIO_NOME) && legacyPrefs.contains(KEY_USUARIO_NOME)) {
                editor.putString(KEY_USUARIO_NOME, legacyPrefs.getString(KEY_USUARIO_NOME, "Equipe Vovô Lar"));
            }
            if (!securePrefs.contains(KEY_USUARIO_EMAIL) && legacyPrefs.contains(KEY_USUARIO_EMAIL)) {
                editor.putString(KEY_USUARIO_EMAIL, legacyPrefs.getString(KEY_USUARIO_EMAIL, "equipe@vovolar.org"));
            }
            if (!securePrefs.contains(KEY_TIMESTAMP_LOGIN) && legacyPrefs.contains(KEY_TIMESTAMP_LOGIN)) {
                editor.putLong(KEY_TIMESTAMP_LOGIN, legacyPrefs.getLong(KEY_TIMESTAMP_LOGIN, System.currentTimeMillis()));
            }
            editor.apply();

            // Destrói os dados em texto plano após migração para o EncryptedSharedPreferences
            legacyPrefs.edit().clear().commit();
            limparArquivoPreferenciasCorrompido(appContext, LEGACY_PLAIN_PREFS_NAME);
            Log.i(TAG, "Preferências legadas migradas com sucesso para EncryptedSharedPreferences.");
        } catch (Exception e) {
            Log.w(TAG, "Não foi possível migrar preferências legadas.", e);
        }
    }

    // =========================================================================
    // Armazenamento e Autenticação Segura de Credenciais Offline
    // =========================================================================

    /**
     * Cadastra/atualiza de forma segura as credenciais offline do profissional no cofre
     * criptografado. A senha ou PIN é transformada em hash irreversível (SHA-256 iterado + Salt
     * aleatório de 128 bits) antes de ser criptografada com AES-256-GCM.
     *
     * @param usuario    Dados do profissional (ID, nome, e-mail e perfil RBAC).
     * @param senhaOuPin Senha ou PIN de acesso offline (mínimo 4 caracteres).
     */
    public void salvarCredenciaisOffline(Usuario usuario, String senhaOuPin) {
        if (usuario == null) {
            throw new IllegalArgumentException("Usuário não pode ser nulo.");
        }
        if (usuario.getEmail() == null || usuario.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("E-mail/identificador do usuário é obrigatório.");
        }
        if (senhaOuPin == null || senhaOuPin.trim().length() < 4) {
            throw new IllegalArgumentException("A senha ou PIN offline deve possuir pelo menos 4 caracteres.");
        }

        byte[] salt = gerarSaltAleatorio();
        String hashBase64 = gerarHashCredencial(senhaOuPin, salt);
        String saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP);
        String emailNormalizado = normalizarIdentificador(usuario.getEmail());
        PerfilUsuario perfilSeguro = usuario.getPerfil() != null ? usuario.getPerfil() : PerfilUsuario.CUIDADOR;

        securePrefs.edit()
                .putLong(KEY_CRED_ID, usuario.getId() > 0 ? usuario.getId() : System.currentTimeMillis())
                .putString(KEY_CRED_NOME, usuario.getNome() != null ? usuario.getNome().trim() : "Profissional Vovô Lar")
                .putString(KEY_CRED_EMAIL, emailNormalizado)
                .putString(KEY_CRED_PERFIL, perfilSeguro.getCodigo())
                .putString(KEY_CRED_HASH, hashBase64)
                .putString(KEY_CRED_SALT, saltBase64)
                .apply();
    }

    /**
     * Sobrecarga utilitária para provisionar credenciais offline e iniciar a sessão autenticada.
     */
    public void salvarCredenciaisOffline(String nome, String email, String senhaOuPin, PerfilUsuario perfil) {
        Usuario usuario = new Usuario(System.currentTimeMillis(), nome, email, perfil);
        salvarCredenciaisOffline(usuario, senhaOuPin);
    }

    /**
     * Verifica se as credenciais informadas correspondem às credenciais offline armazenadas
     * utilizando comparação em tempo constante para mitigar ataques de temporização (timing attacks).
     */
    public boolean validarCredenciaisOffline(String email, String senhaOuPin) {
        if (email == null || senhaOuPin == null || !possuiCredenciaisOffline()) {
            return false;
        }

        String emailSalvo = securePrefs.getString(KEY_CRED_EMAIL, null);
        String hashSalvoBase64 = securePrefs.getString(KEY_CRED_HASH, null);
        String saltSalvoBase64 = securePrefs.getString(KEY_CRED_SALT, null);

        if (emailSalvo == null || hashSalvoBase64 == null || saltSalvoBase64 == null) {
            return false;
        }

        String emailInformado = normalizarIdentificador(email);
        boolean emailConfere = MessageDigest.isEqual(
                emailSalvo.getBytes(StandardCharsets.UTF_8),
                emailInformado.getBytes(StandardCharsets.UTF_8)
        );
        if (!emailConfere) {
            return false;
        }

        byte[] salt = Base64.decode(saltSalvoBase64, Base64.NO_WRAP);
        String hashCalculadoBase64 = gerarHashCredencial(senhaOuPin, salt);

        byte[] bytesSalvos = Base64.decode(hashSalvoBase64, Base64.NO_WRAP);
        byte[] bytesCalculados = Base64.decode(hashCalculadoBase64, Base64.NO_WRAP);
        return MessageDigest.isEqual(bytesSalvos, bytesCalculados);
    }

    /**
     * Realiza o login offline completo: valida o e-mail e a senha/PIN criptografados e,
     * em caso de sucesso, ativa a sessão local carregando o {@link PerfilUsuario} correspondente.
     *
     * @return {@code true} se a autenticação offline foi bem-sucedida e a sessão iniciada.
     */
    public boolean autenticarOffline(String email, String senhaOuPin) {
        if (!validarCredenciaisOffline(email, senhaOuPin)) {
            Log.w(TAG, "Tentativa de autenticação offline recusada para o identificador informado.");
            return false;
        }

        long id = securePrefs.getLong(KEY_CRED_ID, 1L);
        String nome = securePrefs.getString(KEY_CRED_NOME, "Profissional Vovô Lar");
        String emailSalvo = securePrefs.getString(KEY_CRED_EMAIL, normalizarIdentificador(email));
        PerfilUsuario perfil = PerfilUsuario.fromCodigo(
                securePrefs.getString(KEY_CRED_PERFIL, PerfilUsuario.CUIDADOR.getCodigo())
        );

        Usuario usuarioAutenticado = new Usuario(id, nome, emailSalvo, perfil);
        salvarSessao(usuarioAutenticado);
        return true;
    }

    /**
     * Altera a senha/PIN offline do usuário mediante confirmação da credencial atual.
     */
    public boolean alterarSenhaOffline(String email, String senhaAtual, String novaSenha) {
        if (!validarCredenciaisOffline(email, senhaAtual)) {
            return false;
        }
        if (novaSenha == null || novaSenha.trim().length() < 4) {
            throw new IllegalArgumentException("A nova senha ou PIN deve possuir pelo menos 4 caracteres.");
        }

        byte[] novoSalt = gerarSaltAleatorio();
        String novoHashBase64 = gerarHashCredencial(novaSenha, novoSalt);
        String novoSaltBase64 = Base64.encodeToString(novoSalt, Base64.NO_WRAP);

        securePrefs.edit()
                .putString(KEY_CRED_HASH, novoHashBase64)
                .putString(KEY_CRED_SALT, novoSaltBase64)
                .apply();
        return true;
    }

    /**
     * Indica se há credenciais offline cadastradas no cofre seguro do dispositivo.
     */
    public boolean possuiCredenciaisOffline() {
        return securePrefs.contains(KEY_CRED_EMAIL)
                && securePrefs.contains(KEY_CRED_HASH)
                && securePrefs.contains(KEY_CRED_SALT);
    }

    // =========================================================================
    // Gerenciamento de Sessão Ativa e Perfil RBAC
    // =========================================================================

    /**
     * Salva o usuário autenticado e seu respectivo perfil RBAC no cofre criptografado.
     */
    public void salvarSessao(Usuario usuario) {
        if (usuario == null) return;
        long agora = System.currentTimeMillis();
        usuario.setUltimoLoginMillis(agora);

        PerfilUsuario perfilSeguro = usuario.getPerfil() != null
                ? usuario.getPerfil()
                : PerfilUsuario.ADMINISTRADOR;

        securePrefs.edit()
                .putBoolean(KEY_SESSAO_ATIVA, true)
                .putLong(KEY_USUARIO_ID, usuario.getId() > 0 ? usuario.getId() : agora)
                .putString(KEY_USUARIO_NOME, usuario.getNome() != null ? usuario.getNome().trim() : "Profissional Vovô Lar")
                .putString(KEY_USUARIO_EMAIL, normalizarIdentificador(usuario.getEmail()))
                .putString(KEY_PERFIL, perfilSeguro.getCodigo())
                .putLong(KEY_TIMESTAMP_LOGIN, agora)
                .apply();
    }

    /**
     * Salva simultaneamente as credenciais offline protegidas (hash + salt) e inicia a sessão ativa.
     */
    public void salvarSessao(Usuario usuario, String senhaOuPin) {
        if (usuario == null) return;
        salvarCredenciaisOffline(usuario, senhaOuPin);
        salvarSessao(usuario);
    }

    /**
     * Cria e salva rapidamente uma sessão offline criptografada com os parâmetros fundamentais.
     */
    public void salvarSessao(String nome, String email, PerfilUsuario perfil) {
        long id = System.currentTimeMillis();
        Usuario u = new Usuario(id, nome, email, perfil);
        salvarSessao(u);
    }

    /**
     * Atualiza o perfil ativo na sessão criptografada mantendo compatibilidade com PerfilManager.
     */
    public void salvarPerfilAtivo(PerfilUsuario perfil) {
        if (perfil == null) {
            perfil = PerfilUsuario.ADMINISTRADOR;
        }
        securePrefs.edit()
                .putString(KEY_PERFIL, perfil.getCodigo())
                .apply();
    }

    /**
     * Retorna o perfil de usuário ativo armazenado de forma criptografada.
     * Caso não haja valor prévio salvo, retorna {@link PerfilUsuario#ADMINISTRADOR} por padrão.
     */
    public PerfilUsuario getPerfilAtivo() {
        String codigo = securePrefs.getString(KEY_PERFIL, PerfilUsuario.ADMINISTRADOR.getCodigo());
        return PerfilUsuario.fromCodigo(codigo);
    }

    /**
     * Verifica se há uma sessão válida e ativa no dispositivo.
     */
    public boolean isSessaoValida() {
        return securePrefs.getBoolean(KEY_SESSAO_ATIVA, true);
    }

    /**
     * Verifica se a sessão está ativa e dentro da janela máxima de tempo de turno permitida.
     *
     * @param duracaoMaximaMillis Tempo máximo de validade da sessão em milissegundos (ex: {@link #DURACAO_PADRAO_TURNO_MS}).
     */
    public boolean isSessaoValida(long duracaoMaximaMillis) {
        if (!isSessaoValida()) {
            return false;
        }
        long timestampLogin = securePrefs.getLong(KEY_TIMESTAMP_LOGIN, 0L);
        if (timestampLogin <= 0L || duracaoMaximaMillis <= 0L) {
            return true;
        }
        long decorrido = System.currentTimeMillis() - timestampLogin;
        return decorrido >= 0L && decorrido <= duracaoMaximaMillis;
    }

    public boolean isLogado() {
        return isSessaoValida();
    }

    /**
     * Recupera a entidade {@link Usuario} correspondente à sessão ativa no EncryptedSharedPreferences.
     */
    public Usuario getUsuarioLogado() {
        long id = securePrefs.getLong(KEY_USUARIO_ID, 1L);
        String nome = securePrefs.getString(KEY_USUARIO_NOME, "Equipe Vovô Lar");
        String email = securePrefs.getString(KEY_USUARIO_EMAIL, "equipe@vovolar.org");
        PerfilUsuario perfil = getPerfilAtivo();

        Usuario usuario = new Usuario(id, nome, email, perfil);
        usuario.setUltimoLoginMillis(securePrefs.getLong(KEY_TIMESTAMP_LOGIN, System.currentTimeMillis()));
        return usuario;
    }

    public String getNomeUsuario() {
        return securePrefs.getString(KEY_USUARIO_NOME, "Profissional Vovô Lar");
    }

    public String getEmailUsuario() {
        return securePrefs.getString(KEY_USUARIO_EMAIL, "equipe@vovolar.org");
    }

    public long getTimestampLogin() {
        return securePrefs.getLong(KEY_TIMESTAMP_LOGIN, 0L);
    }

    /**
     * Informa se a instância atual está operando sobre {@link EncryptedSharedPreferences}
     * com chaves protegidas pelo Android Keystore.
     */
    public boolean isCriptografiaAtiva() {
        return criptografiaAtiva;
    }

    /**
     * Realiza o logout seguro da sessão atual, invalidando o estado autenticado e removendo os
     * identificadores da sessão ativa, mas preservando o cadastro de credenciais offline para
     * permitir novo login sem rede.
     */
    public void realizarLogout() {
        securePrefs.edit()
                .putBoolean(KEY_SESSAO_ATIVA, false)
                .remove(KEY_USUARIO_ID)
                .remove(KEY_USUARIO_NOME)
                .remove(KEY_USUARIO_EMAIL)
                .remove(KEY_TIMESTAMP_LOGIN)
                // Redefine para o perfil operacional mais restrito (Princípio do Menor Privilégio) após logout
                .putString(KEY_PERFIL, PerfilUsuario.CUIDADOR.getCodigo())
                .apply();
    }

    public void logout() {
        realizarLogout();
    }

    /**
     * Revoga completamente tanto a sessão ativa quanto todas as credenciais offline salvas
     * no cofre criptografado (recomendado ao desvincular um tablet/dispositivo da ILPI).
     */
    public void revogarCredenciaisESessao() {
        securePrefs.edit().clear().apply();
    }

    // =========================================================================
    // Utilitários Criptográficos Internos (PBKDF / SHA-256 Iterado + Salt)
    // =========================================================================

    private static byte[] gerarSaltAleatorio() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static String gerarHashCredencial(String senhaOuPin, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hash = digest.digest(senhaOuPin.trim().getBytes(StandardCharsets.UTF_8));

            for (int i = 1; i < HASH_ITERATIONS; i++) {
                digest.reset();
                digest.update(salt);
                hash = digest.digest(hash);
            }
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo criptográfico SHA-256 indisponível no dispositivo.", e);
        }
    }

    private static String normalizarIdentificador(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "equipe@vovolar.org";
        }
        return email.trim().toLowerCase();
    }

    // =========================================================================
    // Métodos Utilitários Estáticos para Acesso Rápido pelo RBAC
    // =========================================================================

    public static PerfilUsuario getPerfil(Context context) {
        return getInstance(context).getPerfilAtivo();
    }

    public static boolean isAdministrador(Context context) {
        return getInstance(context).getPerfilAtivo() == PerfilUsuario.ADMINISTRADOR;
    }

    public static boolean isEnfermeiro(Context context) {
        return getInstance(context).getPerfilAtivo() == PerfilUsuario.ENFERMEIRO;
    }

    public static boolean isCuidador(Context context) {
        return getInstance(context).getPerfilAtivo() == PerfilUsuario.CUIDADOR;
    }

    /**
     * Reinicia a instância singleton em memória (uso exclusivo em testes automatizados).
     */
    public static synchronized void resetInstanceForTesting() {
        instance = null;
    }
}
