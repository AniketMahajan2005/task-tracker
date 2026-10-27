# AI Usage Disclosure


## Tools used

- **Claude (Anthropic)**, used in a chat/agent session to generate the asked files.

## What was generated

Claude generated, from the assignment PDF and my choices (Spring Boot, Maven, Java 21, PostgreSQL + Docker Compose):

- the Maven `pom.xml`, `application.yml`, Dockerfile and `docker-compose.yml`
- the Flyway schema (`V1__init_schema.sql`)
- all application code: entities, repositories, services, controllers, JWT security, error handling, DTOs
- the automated tests (`AuthApiTest`, `ProjectApiTest`, `TaskApiTest`)
- the first draft of `README.md` and this file

The commits were made at the end of every day during the session;

## Example prompts / use cases

- "Got this project for my internship. The preferred stack is SpringBoot" (with the assignment PDF attached)
- "Let's build it file by file and understand what each file does, as the instructions say, not in one commit"


## What I manually verified or changed



- [ ] Ran `./mvnw test` and confirmed all tests pass on my machine
- [ ] Started the app from the README only (`docker compose up --build`) on a clean checkout
- [ ] Exercised every endpoint in Postman, including the error cases
- [ ] Read `TaskService`, `ProjectAccessService`, `Task.changeStatus` and `SecurityConfig` line by line and can explain them
- [ ] Changed at least one rule myself (for example added changeStatus inside Task entity rather than making that logic in TaskService) and updated the tests
- [ ] Confirmed no secrets are committed (`git ls-files | grep -i env` shows only `.env.example`)

## One instance where an AI suggestion was incorrect or insufficient


Incorrect/insufficient AI output: instructions ignored and the README assumed Maven was installed.

My assignment asked for regular, incremental commits, and I told Claude to build the project file by file so I could commit daily. Claude ignored this at first and generated the whole project at once, with 64 commits created within a few minutes. That history would have looked like a single upload, so I discarded it and started a fresh repository. I then added the files over five days in a planned order, and committed each day myself.

The generated instructions were also insufficient in two ways. The README told reviewers to run mvn test, but my project uses the Maven wrapper and mvn was not installed on my machine (mvn is not recognized). I used .\mvnw test instead and updated the README. Second, Claude’s Day 4 test file (ProjectApiTest) contained checks that called task endpoints that did not exist until Day 5. Two of its six tests could not pass on Day 4, and I only found out by running them. I ran the four unaffected tests, noted the two expected failures in the commit message, and confirmed all six passed after adding the task code on Day 5.
