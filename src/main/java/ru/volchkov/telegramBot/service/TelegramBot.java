package ru.volchkov.telegramBot.service;


import com.vdurmont.emoji.EmojiParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.volchkov.telegramBot.config.BotConfig;
import ru.volchkov.telegramBot.model.Book;
import ru.volchkov.telegramBot.model.Person;
import ru.volchkov.telegramBot.repository.BookRepository;
import ru.volchkov.telegramBot.model.PersonStatus;
import ru.volchkov.telegramBot.repository.PersonRepository;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {
    private final BookRepository bookRepository;
    private final PersonRepository personRepository;
    private final WordService wordService;
    private final BookService bookService;
    private final PersonService personService;
    private final BotConfig config;
    private final String regex = "\\d+";
    private static final String HELP_TEXT = "1. Добавление, изменение и удаление книг\n" + "2. Страница со списком всех людей\n" + "3. Страница со списком всех книг\n" + "4. Страница человека, на которой показаны значения его полей и список книг которые он взял.\n" + "6. Возможность освободить книгу";
    private static final String INFO_TEXT = EmojiParser.parseToUnicode("Бот взят за основу разработки API приложения, чтобы реализовать" + " бизнес-логику проекта без html кода.\n                                                     " + ":heavy_check_mark:Задача:heavy_check_mark:\n" + "В местной библиотеке хотят перейти на цифровой учет книг. Нам " + "было необходимо реализовать приложение для них. Библиотекари " + "должны иметь возможность регистрировать читателей, выдавать им " + "книги и освобождать книги (после того, как читатель возвращает " + "книгу обратно в библиотеку).");
    private static final String YES_BUTTON = "YES_BUTTON";
    private static final String NO_BUTTON = "NO_BUTTON";
    private int anInt;

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {

        long id = 0;
        String textFromUpdate = null;
        if (update.hasMessage()) {
            textFromUpdate = update.getMessage().getText();
            id = update.getMessage().getChat().getId();
        } else if (update.hasCallbackQuery()) {
            id = update.getCallbackQuery().getMessage().getChat().getId();
        }
        if (update.hasMessage() && personRepository.findPersonById(id).isEmpty()) {
            executeMessage(personService.startCommand(update));
        }
        try {
            anInt = Integer.parseInt(textFromUpdate);
        } catch (Exception e) {
            log.info("Error");
        }
        Person personById = personRepository.findPersonById(id).orElseThrow();
        boolean personIsUser = personById.getPersonStatus().equals(PersonStatus.USER);
        if (update.hasCallbackQuery()) {
            String name = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            String chatIdFromQuery = String.valueOf(update.getCallbackQuery().getMessage().getChatId());
            long peopleId = update.getCallbackQuery().getMessage().getChat().getId();
            boolean bookIsPresent = bookRepository.findBookByName(name).isPresent();
            if (bookIsPresent && personIsUser
                    && getBook(name).orElseThrow().getPerson() == null) {
                executeMessage(bookService.takeBook(peopleId, name, chatIdFromQuery, messageId));

            } else if (bookIsPresent && getBook(name).orElseThrow().getPerson() != null
                    && personIsUser
                    && update.getCallbackQuery().getMessage().getChat().getId().equals(getBook(name).orElseThrow().getPerson().getId())) {
                executeMessage(bookService.giveBook(name, update, chatIdFromQuery, messageId));
            }
            if (name.equals("listOfUsers") && personIsUser) {
                personService.listOfUsers(chatIdFromQuery, personRepository).forEach(this::executeMessage);
            }
            if (personRepository.findPersonByName(name).isPresent() && personIsUser) {
                personService.userInfo(getPersonByName(name).orElseThrow().getId(), chatIdFromQuery).forEach(this::executeMessage);
            }
            if (name.equals(YES_BUTTON)) {
                executeEditMessageText("Сколько вам лет?", chatIdFromQuery, messageId);

            }
            if (name.equals(NO_BUTTON)) {
                String text = EmojiParser.parseToUnicode("Вы нажали на кнопку NO:cry:");
                executeEditMessageText(text, chatIdFromQuery, messageId);
            }
        }
        boolean personIsGuest = personById.getPersonStatus().equals(PersonStatus.GUEST);
        String chatIdFromUpdate = String.valueOf(update.getMessage().getChatId());
        if (update.hasMessage() && personById.getPersonStatus().equals(PersonStatus.TAPING_NAME_BOOK)
                && bookRepository.findBookByName("test").isPresent()) {
            executeMessage(bookService.addBookName(textFromUpdate, chatIdFromUpdate, personById));
            return;
        }
        if (update.hasMessage() && personById.getPersonStatus().equals(PersonStatus.TAPING_AUTHOR_BOOK)
                && bookRepository.findBookByAuthor("test").isPresent() && bookRepository.findBookByName("test").isEmpty()) {
            executeMessage(bookService.addBookAuthor(textFromUpdate, chatIdFromUpdate, personById));
            return;
        }
        if (update.hasMessage() && textFromUpdate.matches(regex) && bookRepository.findBookByName("test").isEmpty()
                && bookRepository.findBookByAuthor("test").isEmpty() && bookRepository.findBookByYearOfRelease(1).isPresent()
                && personById.getPersonStatus().equals(PersonStatus.TAPING_YEAR_OF_BOOK)) {
            executeMessage(bookService.addBookYear(anInt, chatIdFromUpdate, id));
            return;
        }
        if (update.hasMessage() && textFromUpdate.equals("/register") && personIsGuest) {
            executeMessage(personService.register(update));
        }
        if (update.hasMessage() && personRepository.findPersonByName(wordService.firstUpperCase(textFromUpdate)).isPresent()
                && personIsUser) {
            executeMessage(personService.listOfUser(chatIdFromUpdate, wordService.firstUpperCase(textFromUpdate)));

        }
        if (update.hasMessage() && textFromUpdate.matches(regex) && anInt <= 100 && anInt > 0 && personIsGuest) {
            executeMessage(personService.setStatusUSER(update));
        }
        if (update.hasMessage() && textFromUpdate.matches(regex) && anInt > 100 && personIsGuest || anInt < 0) {
            prepareAndSendMessage(chatIdFromUpdate, "Ошибка ввода");
        }
        if (update.hasMessage() && personIsUser && !textFromUpdate.matches(regex)
                && personRepository.findPersonByName(wordService.firstUpperCase(textFromUpdate)).isEmpty()) {

            switch (textFromUpdate) {
                case "/start" -> startCommandReceived(chatIdFromUpdate, update.getMessage().getChat().getFirstName());
                case "/info" -> prepareAndSendMessage(chatIdFromUpdate, INFO_TEXT);
                case "/help" -> prepareAndSendMessage(chatIdFromUpdate, HELP_TEXT);
                case "/listofusers" ->
                        personService.userMenu(chatIdFromUpdate, personRepository).forEach(this::executeMessage);
                case "/takebook" -> bookService.allBooks(chatIdFromUpdate).forEach(this::executeMessage);
                case "/addbook" ->
                        executeMessage(bookService.addNewBook(update.getMessage().getChat().getId(), chatIdFromUpdate));
                default ->
                        prepareAndSendMessage(chatIdFromUpdate, EmojiParser.parseToUnicode(update.getMessage().getChat().getFirstName()
                                + ",Вы ввели неверную команду,попробуйте еще раз :unamused:"));
            }
        }
    }

    private Optional<Person> getPersonByName(String name) {
        return personRepository.findPersonByName(name);
    }

    private Optional<Book> getBook(String name) {
        return bookRepository.findBookByName(name);
    }

    private void startCommandReceived(String chatId, String name) {
        String answer = EmojiParser.parseToUnicode("Привет, " + name + ", добро пожаловать в библиотеку с цифровым учетом книг "
                + ":blush: . \n" + "Для описания фунционала нажмите на команду /help\n" + "Для информации о проекте нажмите /info");
        log.info("Replied to user " + name);
        prepareAndSendMessage(chatId, answer);
    }

    private void executeEditMessageText(String text, String chatId, long messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setText(text);
        message.setMessageId((int) messageId);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Message sending error" + e.getMessage());
        }
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Message sending error" + e.getMessage());
        }
    }

    private void executeMessage(EditMessageText message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Message sending error" + e.getMessage());
        }
    }

    private void prepareAndSendMessage(String chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);
        executeMessage(message);
    }

    @PostConstruct
    public void runAfterStartup() {
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "получить начальную информацию"));
        listOfCommands.add(new BotCommand("/register", "регистрация нового пользователя"));
        listOfCommands.add(new BotCommand("/info", "описание проекта"));
        listOfCommands.add(new BotCommand("/help", "описание функционала"));
        listOfCommands.add(new BotCommand("/listofusers", "вывести всех зарегестрированных людей"));
        listOfCommands.add(new BotCommand("/addbook", "добавить книгу в библиотеку"));
        listOfCommands.add(new BotCommand("/takebook", "посмотреть список книг, взять книгу"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }
}
