# Cuidar+ (Gestão Assistencial para ILPI)

Aplicativo Android nativo desenvolvido sob o paradigma **Local-First (100% offline)** para modernizar a rotina de cuidados, o prontuário eletrônico e o controle de medicamentos da Instituição de Longa Permanência para Idosos (ILPI) **Vovô Lar**.

## Principais Funcionalidades

* **Prontuário Digital Offline:** Cadastro completo de residentes com foto (CameraX), tipagem sanguínea, alergias em destaque e contatos de emergência.
* **Gestão de Medicamentos e Alarmes Nativos:** Aprazamento automático de doses com alertas sonoros e notificações de alta prioridade (`AlarmManager` e `BroadcastReceiver`), mesmo com a tela bloqueada ou após reinicialização do aparelho.
* **Controle e Baixa Automática de Estoque:** Dedução transacional a cada dose administrada e alertas visuais para itens abaixo do limite mínimo configurável.
* **Relatórios e Comunicação:** Geração nativa de prontuários e inventário de estoque em **PDF (A4)** 100% no dispositivo e acionamento rápido de familiares via **WhatsApp** ou discador telefônico.
* **Dashboard e Controle de Acesso (RBAC):** Indicadores de ocupação de leitos, aniversariantes do mês e perfis distintos (*Administrador*, *Enfermeiro* e *Cuidador*) com sessão criptografada (`EncryptedSharedPreferences`).

## Tecnologias Utilizadas

* **Linguagem:** Java Nativo (Android SDK, API 24+)
* **Interface:** XML com Material Design 3 (MD3)
* **Banco de Dados:** SQLite relacional local (padrão DAO)
* **APIs Nativas:** `CameraX`, `PdfDocument`, `FileProvider`, `AlarmManager` e `EncryptedSharedPreferences`

## Equipe de Desenvolvimento

* **Arthur Senra Homercher** — Analista / Desenvolvedor
* **Gilmar Ribeiro Fragoso Júnior** — Desenvolvedor / Testador
* **Joaquim Neinas** — Desenvolvedor / Documentador
