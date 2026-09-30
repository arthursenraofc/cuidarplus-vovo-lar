# CuidarPlus — Sistema de Gestão de ILPI (Lar Vovô Lar)

**Status do Projeto:** Sprint 1 Concluída (Versão 1.0)  
**Arquitetura:** Local-First (Offline)  
**Plataforma:** Android Nativo (Java)  

---

## 1. Introdução

O **CuidarPlus** é uma solução mobile desenvolvida para otimizar os processos de acompanhamento e gestão clínica na Instituição de Longa Permanência para Idosos (ILPI) **Vovô Lar**. O sistema foi projetado sob a premissa *Local-First*, permitindo operação 100% offline, sem custos recorrentes de servidores ou infraestrutura de nuvem.

---

## 2. Funcionalidades Principais

* **Gestão de Idosos:** Cadastro completo de residentes, consulta de fichas e prontuários individuais.
* **Controlo de Medicação:** Registo de medicamentos, horários de administração e doses prescritas.
* **Alertas e Notificações:** Notificações locais agendadas para garantir o cumprimento dos horários de medicação.
* **Operação Offline:** Armazenamento local seguro através de SQLite, garantindo alta disponibilidade sem dependência de internet.

---

## 3. Tecnologias Utilizadas

* **Linguagem Principal:** Java (Android Nativo)
* **Interface:** XML / Material Design
* **Base de Dados Local:** SQLite (`DatabaseHelper`, DAOs)
* **Gestão de Alertas:** `AlarmManager` e `BroadcastReceiver`
* **Build System:** Gradle (Versão 9.3.1)

---

## 4. Como Executar o Projeto

1. Clone o repositório:
   ```bash
   git clone [https://github.com/arthursenraofc/cuidarplus-vovo-lar.git](https://github.com/arthursenraofc/cuidarplus-vovo-lar.git)
