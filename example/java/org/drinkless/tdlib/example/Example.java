//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2022
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
package org.drinkless.tdlib.example;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Example class for TDLib usage from Java.
 */
public final class Example {

    private static Client client = null;

    private static TdApi.AuthorizationState authorizationState = null;
    private static volatile boolean haveAuthorization = false;
    private static volatile boolean needQuit = false;
    private static volatile boolean canQuit = false;

    private static final Client.ResultHandler defaultHandler = new DefaultHandler();

    private static final Lock authorizationLock = new ReentrantLock();
    private static final Condition gotAuthorization = authorizationLock.newCondition();

    private static final ConcurrentMap<Long, TdApi.User> users = new ConcurrentHashMap<Long, TdApi.User>();
    private static final ConcurrentMap<Long, TdApi.BasicGroup> basicGroups = new ConcurrentHashMap<Long, TdApi.BasicGroup>();
    private static final ConcurrentMap<Long, TdApi.Supergroup> supergroups = new ConcurrentHashMap<Long, TdApi.Supergroup>();
    private static final ConcurrentMap<Integer, TdApi.SecretChat> secretChats = new ConcurrentHashMap<Integer, TdApi.SecretChat>();

    private static final ConcurrentMap<Long, TdApi.Chat> chats = new ConcurrentHashMap<Long, TdApi.Chat>();
    private static final NavigableSet<OrderedChat> mainChatList = new TreeSet<OrderedChat>();
    private static boolean haveFullMainChatList = false;

    private static final ConcurrentMap<Long, TdApi.UserFullInfo> usersFullInfo = new ConcurrentHashMap<Long, TdApi.UserFullInfo>();
    private static final ConcurrentMap<Long, TdApi.BasicGroupFullInfo> basicGroupsFullInfo = new ConcurrentHashMap<Long, TdApi.BasicGroupFullInfo>();
    private static final ConcurrentMap<Long, TdApi.SupergroupFullInfo> supergroupsFullInfo = new ConcurrentHashMap<Long, TdApi.SupergroupFullInfo>();

    private static final String newLine = System.getProperty("line.separator");
    private static final String commandsLine = "Enter command (\n" +
        "reportPornography <text>/<chatName>/<messageId>/.../<messageId>,\n" +
        "reportCopyright <text>/<chatName>/<messageId>/.../<messageId>,\n" +
        "reportViolence <text>/<chatName>/<messageId>/.../<messageId>,\n" +
        "reportSpam <text>/<chatName>/<messageId>/.../<messageId>,\n" +
        "reportFake <text>/<chatName>/<messageId>/.../<messageId>,\n" +
        "reportChildAbuse <text>/<chatName>/<messageId>/.../<messageId>,\n" +
        "reportCustom <text>/<chatName>/<messageId>/.../<messageId>,\n" +
        "reportMessages <full_path_to_resolved_file>,\n" +
        "reportChannels <full_path_to_resolved_file>,\n" +
        "resolveIds <full_path_to_file>,\n" +
        "gcs - GetChats,\n" +
        "gc <chatId> - GetChat,\n" +
        "gc <chatId> <messageId> - GetMessage,\n" +
        "sc <chatName> - SearchChat,\n" +
        "gml <chatName> <messageId> - GetMessageLinkInfo,\n" +
        "me - GetMe,\n" +
        "sm <chatId> <message> - SendMessage,\n" +
        "lo - LogOut,\n" +
        "q - Quit): ";
    private static volatile String currentPrompt = null;

    static {
        try {
            System.loadLibrary("tdjni");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
    }

    private static void print(String str) {
        if (currentPrompt != null) {
            System.out.println("");
        }
        System.out.println(str);
        if (currentPrompt != null) {
            System.out.print(currentPrompt);
        }
    }

    private static void setChatPositions(TdApi.Chat chat, TdApi.ChatPosition[] positions) {
        synchronized (mainChatList) {
            synchronized (chat) {
                for (TdApi.ChatPosition position : chat.positions) {
                    if (position.list.getConstructor() == TdApi.ChatListMain.CONSTRUCTOR) {
                        boolean isRemoved = mainChatList.remove(new OrderedChat(chat.id, position));
                        assert isRemoved;
                    }
                }

                chat.positions = positions;

                for (TdApi.ChatPosition position : chat.positions) {
                    if (position.list.getConstructor() == TdApi.ChatListMain.CONSTRUCTOR) {
                        boolean isAdded = mainChatList.add(new OrderedChat(chat.id, position));
                        assert isAdded;
                    }
                }
            }
        }
    }

    private static void onAuthorizationStateUpdated(TdApi.AuthorizationState authorizationState) {
        if (authorizationState != null) {
            Example.authorizationState = authorizationState;
        }
        switch (Example.authorizationState.getConstructor()) {
            case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR:
                TdApi.TdlibParameters parameters = new TdApi.TdlibParameters();
                parameters.databaseDirectory = "tdlib";
                parameters.useMessageDatabase = true;
                parameters.useSecretChats = true;
                parameters.apiId = 94575;
                parameters.apiHash = "a3406de8d171bb422bb6ddf3bbd800e2";
                parameters.systemLanguageCode = "en";
                parameters.deviceModel = "Desktop";
                parameters.applicationVersion = "1.0";
                parameters.enableStorageOptimizer = true;

                client.send(new TdApi.SetTdlibParameters(parameters), new AuthorizationRequestHandler());
                break;
            case TdApi.AuthorizationStateWaitEncryptionKey.CONSTRUCTOR:
                client.send(new TdApi.CheckDatabaseEncryptionKey(), new AuthorizationRequestHandler());
                break;
            case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR: {
                String phoneNumber = promptString("Please enter phone number: ");
                client.send(new TdApi.SetAuthenticationPhoneNumber(phoneNumber, null), new AuthorizationRequestHandler());
                break;
            }
            case TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR: {
                String link = ((TdApi.AuthorizationStateWaitOtherDeviceConfirmation) Example.authorizationState).link;
                System.out.println("Please confirm this login link on another device: " + link);
                break;
            }
            case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR: {
                String code = promptString("Please enter authentication code: ");
                client.send(new TdApi.CheckAuthenticationCode(code), new AuthorizationRequestHandler());
                break;
            }
            case TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR: {
                String firstName = promptString("Please enter your first name: ");
                String lastName = promptString("Please enter your last name: ");
                client.send(new TdApi.RegisterUser(firstName, lastName), new AuthorizationRequestHandler());
                break;
            }
            case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR: {
                String password = promptString("Please enter password: ");
                client.send(new TdApi.CheckAuthenticationPassword(password), new AuthorizationRequestHandler());
                break;
            }
            case TdApi.AuthorizationStateReady.CONSTRUCTOR:
                haveAuthorization = true;
                authorizationLock.lock();
                try {
                    gotAuthorization.signal();
                } finally {
                    authorizationLock.unlock();
                }
                break;
            case TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR:
                haveAuthorization = false;
                print("Logging out");
                break;
            case TdApi.AuthorizationStateClosing.CONSTRUCTOR:
                haveAuthorization = false;
                print("Closing");
                break;
            case TdApi.AuthorizationStateClosed.CONSTRUCTOR:
                print("Closed");
                if (!needQuit) {
                    client = Client.create(new UpdateHandler(), null, null); // recreate client after previous has closed
                } else {
                    canQuit = true;
                }
                break;
            default:
                System.err.println("Unsupported authorization state:" + newLine + Example.authorizationState);
        }
    }

    private static int toInt(String arg) {
        int result = 0;
        try {
            result = Integer.parseInt(arg);
        } catch (NumberFormatException ignored) {
        }
        return result;
    }

    private static long getChatId(String arg) {
        long chatId = 0;
        try {
            chatId = Long.parseLong(arg);
        } catch (NumberFormatException ignored) {
        }
        return chatId;
    }

    private static String promptString(String prompt) {
        System.out.print(prompt);
        currentPrompt = prompt;
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String str = "";
        try {
            str = reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        currentPrompt = null;
        return str;
    }

    private static void getCommand() {
        String command = promptString(commandsLine);
        String[] commands = command.split(" ", 2);
        try {
            switch (commands[0]) {
                case "gcs": {
                    int limit = 20;
                    if (commands.length > 1) {
                        limit = toInt(commands[1]);
                    }
                    getMainChatList(limit);
                    break;
                }
                case "gc":
                    client.send(new TdApi.GetChat(getChatId(commands[1])), defaultHandler);
                    break;
                case "gm":
                    String[] values = commands[1].split(" ");
                    client.send(new TdApi.GetMessage(getChatId(values[0]), getChatId(values[1])), defaultHandler);
                    break;
                case "gml":
                    String[] data = commands[1].split(" ");
                    client.send(new TdApi.GetMessageLinkInfo("https://t.me/" + data[0] + "/" + data[1]), defaultHandler);
                    break;
                case "sc":
                    client.send(new TdApi.SearchPublicChat(commands[1]), defaultHandler);
                    break;
                case "me":
                    client.send(new TdApi.GetMe(), defaultHandler);
                    break;
                case "sm": {
                    String[] args = commands[1].split(" ", 2);
                    sendMessage(getChatId(args[0]), args[1]);
                    break;
                }
                case "lo":
                    haveAuthorization = false;
                    client.send(new TdApi.LogOut(), defaultHandler);
                    break;
                case "q":
                    needQuit = true;
                    haveAuthorization = false;
                    client.send(new TdApi.Close(), defaultHandler);
                    break;
                case "reportCustom":
                    report(commands[1].split("/"), new TdApi.ChatReportReasonCustom());
                    break;
                case "reportChildAbuse":
                    report(commands[1].split("/"), new TdApi.ChatReportReasonChildAbuse());
                    break;
                case "reportFake":
                    report(commands[1].split("/"), new TdApi.ChatReportReasonFake());
                    break;
                case "reportSpam":
                    report(commands[1].split("/"), new TdApi.ChatReportReasonSpam());
                    break;
                case "reportViolence":
                    report(commands[1].split("/"), new TdApi.ChatReportReasonViolence());
                    break;
                case "reportCopyright":
                    report(commands[1].split("/"), new TdApi.ChatReportReasonCopyright());
                    break;
                case "reportPornography":
                    report(commands[1].split("/"), new TdApi.ChatReportReasonPornography());
                    break;
                case "resolveIds":
                    ReportMessageResolver.resolveIds(Paths.get(commands[1]));
                    break;
                case "reportMessages":
                    ReportMessageExecutor.execute(Paths.get(commands[1]));
                    break;
                case "reportChannels":
                    ReportChannelExecutor.execute(Paths.get(commands[1]));
                    break;
                default:
                    System.err.println("Unsupported command: " + command);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            print("Not enough arguments");
        }
    }

    private static void report(String[] commands, TdApi.ChatReportReason reason) {
        String text = commands[0];
        String channel = commands[1];
        LinkedBlockingQueue<Long> chatIds = new LinkedBlockingQueue<>(commands.length);
        LinkedBlockingQueue<Long> messageIds = new LinkedBlockingQueue<>(commands.length);
        CountDownLatch latch = new CountDownLatch(commands.length - 2);
        for (int i = 2; i < commands.length; i++) {
            client.send(new TdApi.GetMessageLinkInfo("https://t.me/" + channel + "/" + commands[i]), object -> {
                TdApi.MessageLinkInfo info = (TdApi.MessageLinkInfo) object;
                messageIds.add(info.message.id);
                chatIds.add(info.message.chatId);
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        System.out.println("Reporting chat ids:" + chatIds);
        System.out.println("Reporting message ids:" + messageIds);
        client.send(new TdApi.ReportChat(chatIds.poll(), messageIds.stream().mapToLong(Long::longValue).toArray(), reason, text), defaultHandler);
    }

    private static void getMainChatList(final int limit) {
        synchronized (mainChatList) {
            if (!haveFullMainChatList && limit > mainChatList.size()) {
                // send LoadChats request if there are some unknown chats and have not enough known chats
                client.send(new TdApi.LoadChats(new TdApi.ChatListMain(), limit - mainChatList.size()), new Client.ResultHandler() {
                    @Override
                    public void onResult(TdApi.Object object) {
                        switch (object.getConstructor()) {
                            case TdApi.Error.CONSTRUCTOR:
                                if (((TdApi.Error) object).code == 404) {
                                    synchronized (mainChatList) {
                                        haveFullMainChatList = true;
                                    }
                                } else {
                                    System.err.println("Receive an error for LoadChats:" + newLine + object);
                                }
                                break;
                            case TdApi.Ok.CONSTRUCTOR:
                                // chats had already been received through updates, let's retry request
                                getMainChatList(limit);
                                break;
                            default:
                                System.err.println("Receive wrong response from TDLib:" + newLine + object);
                        }
                    }
                });
                return;
            }

            java.util.Iterator<OrderedChat> iter = mainChatList.iterator();
            System.out.println();
            System.out.println("First " + limit + " chat(s) out of " + mainChatList.size() + " known chat(s):");
            for (int i = 0; i < limit && i < mainChatList.size(); i++) {
                long chatId = iter.next().chatId;
                TdApi.Chat chat = chats.get(chatId);
                synchronized (chat) {
                    System.out.println(chatId + ": " + chat.title);
                }
            }
            print("");
        }
    }

    private static void sendMessage(long chatId, String message) {
        // initialize reply markup just for testing
        TdApi.InlineKeyboardButton[] row = {new TdApi.InlineKeyboardButton("https://telegram.org?1", new TdApi.InlineKeyboardButtonTypeUrl()), new TdApi.InlineKeyboardButton("https://telegram.org?2", new TdApi.InlineKeyboardButtonTypeUrl()), new TdApi.InlineKeyboardButton("https://telegram.org?3", new TdApi.InlineKeyboardButtonTypeUrl())};
        TdApi.ReplyMarkup replyMarkup = new TdApi.ReplyMarkupInlineKeyboard(new TdApi.InlineKeyboardButton[][]{row, row, row});

        TdApi.InputMessageContent content = new TdApi.InputMessageText(new TdApi.FormattedText(message, null), false, true);
        client.send(new TdApi.SendMessage(chatId, 0, 0, null, replyMarkup, content), defaultHandler);
    }

    public static void main(String[] args) throws InterruptedException {
        // disable TDLib log
        Client.execute(new TdApi.SetLogVerbosityLevel(0));
        if (Client.execute(new TdApi.SetLogStream(new TdApi.LogStreamFile("tdlib.log", 1 << 27, false))) instanceof TdApi.Error) {
            throw new IOError(new IOException("Write access to the current directory is required"));
        }

        // create client
        client = Client.create(new UpdateHandler(), null, null);

        // test Client.execute
        defaultHandler.onResult(Client.execute(new TdApi.GetTextEntities("@telegram /test_command https://telegram.org telegram.me @gif @test")));

        // main loop
        while (!needQuit) {
            // await authorization
            authorizationLock.lock();
            try {
                while (!haveAuthorization) {
                    gotAuthorization.await();
                }
            } finally {
                authorizationLock.unlock();
            }

            while (haveAuthorization) {
                getCommand();
            }
        }
        while (!canQuit) {
            Thread.sleep(1);
        }
    }

    private static class OrderedChat implements Comparable<OrderedChat> {
        final long chatId;
        final TdApi.ChatPosition position;

        OrderedChat(long chatId, TdApi.ChatPosition position) {
            this.chatId = chatId;
            this.position = position;
        }

        @Override
        public int compareTo(OrderedChat o) {
            if (this.position.order != o.position.order) {
                return o.position.order < this.position.order ? -1 : 1;
            }
            if (this.chatId != o.chatId) {
                return o.chatId < this.chatId ? -1 : 1;
            }
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            OrderedChat o = (OrderedChat) obj;
            return this.chatId == o.chatId && this.position.order == o.position.order;
        }
    }

    private static class DefaultHandler implements Client.ResultHandler {
        @Override
        public void onResult(TdApi.Object object) {
            print(object.toString());
        }
    }

    private static class UpdateHandler implements Client.ResultHandler {
        @Override
        public void onResult(TdApi.Object object) {
            switch (object.getConstructor()) {
                case TdApi.UpdateAuthorizationState.CONSTRUCTOR:
                    onAuthorizationStateUpdated(((TdApi.UpdateAuthorizationState) object).authorizationState);
                    break;

                case TdApi.UpdateUser.CONSTRUCTOR:
                    TdApi.UpdateUser updateUser = (TdApi.UpdateUser) object;
                    users.put(updateUser.user.id, updateUser.user);
                    break;
                case TdApi.UpdateUserStatus.CONSTRUCTOR:  {
                    TdApi.UpdateUserStatus updateUserStatus = (TdApi.UpdateUserStatus) object;
                    TdApi.User user = users.get(updateUserStatus.userId);
                    synchronized (user) {
                        user.status = updateUserStatus.status;
                    }
                    break;
                }
                case TdApi.UpdateBasicGroup.CONSTRUCTOR:
                    TdApi.UpdateBasicGroup updateBasicGroup = (TdApi.UpdateBasicGroup) object;
                    basicGroups.put(updateBasicGroup.basicGroup.id, updateBasicGroup.basicGroup);
                    break;
                case TdApi.UpdateSupergroup.CONSTRUCTOR:
                    TdApi.UpdateSupergroup updateSupergroup = (TdApi.UpdateSupergroup) object;
                    supergroups.put(updateSupergroup.supergroup.id, updateSupergroup.supergroup);
                    break;
                case TdApi.UpdateSecretChat.CONSTRUCTOR:
                    TdApi.UpdateSecretChat updateSecretChat = (TdApi.UpdateSecretChat) object;
                    secretChats.put(updateSecretChat.secretChat.id, updateSecretChat.secretChat);
                    break;

                case TdApi.UpdateNewChat.CONSTRUCTOR: {
                    TdApi.UpdateNewChat updateNewChat = (TdApi.UpdateNewChat) object;
                    TdApi.Chat chat = updateNewChat.chat;
                    synchronized (chat) {
                        chats.put(chat.id, chat);

                        TdApi.ChatPosition[] positions = chat.positions;
                        chat.positions = new TdApi.ChatPosition[0];
                        setChatPositions(chat, positions);
                    }
                    break;
                }
                case TdApi.UpdateChatTitle.CONSTRUCTOR: {
                    TdApi.UpdateChatTitle updateChat = (TdApi.UpdateChatTitle) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.title = updateChat.title;
                    }
                    break;
                }
                case TdApi.UpdateChatPhoto.CONSTRUCTOR: {
                    TdApi.UpdateChatPhoto updateChat = (TdApi.UpdateChatPhoto) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.photo = updateChat.photo;
                    }
                    break;
                }
                case TdApi.UpdateChatLastMessage.CONSTRUCTOR: {
                    TdApi.UpdateChatLastMessage updateChat = (TdApi.UpdateChatLastMessage) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.lastMessage = updateChat.lastMessage;
                        setChatPositions(chat, updateChat.positions);
                    }
                    break;
                }
                case TdApi.UpdateChatPosition.CONSTRUCTOR: {
                    TdApi.UpdateChatPosition updateChat = (TdApi.UpdateChatPosition) object;
                    if (updateChat.position.list.getConstructor() != TdApi.ChatListMain.CONSTRUCTOR) {
                      break;
                    }

                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        int i;
                        for (i = 0; i < chat.positions.length; i++) {
                            if (chat.positions[i].list.getConstructor() == TdApi.ChatListMain.CONSTRUCTOR) {
                                break;
                            }
                        }
                        TdApi.ChatPosition[] new_positions = new TdApi.ChatPosition[chat.positions.length + (updateChat.position.order == 0 ? 0 : 1) - (i < chat.positions.length ? 1 : 0)];
                        int pos = 0;
                        if (updateChat.position.order != 0) {
                          new_positions[pos++] = updateChat.position;
                        }
                        for (int j = 0; j < chat.positions.length; j++) {
                            if (j != i) {
                                new_positions[pos++] = chat.positions[j];
                            }
                        }
                        assert pos == new_positions.length;

                        setChatPositions(chat, new_positions);
                    }
                    break;
                }
                case TdApi.UpdateChatReadInbox.CONSTRUCTOR: {
                    TdApi.UpdateChatReadInbox updateChat = (TdApi.UpdateChatReadInbox) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.lastReadInboxMessageId = updateChat.lastReadInboxMessageId;
                        chat.unreadCount = updateChat.unreadCount;
                    }
                    break;
                }
                case TdApi.UpdateChatReadOutbox.CONSTRUCTOR: {
                    TdApi.UpdateChatReadOutbox updateChat = (TdApi.UpdateChatReadOutbox) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.lastReadOutboxMessageId = updateChat.lastReadOutboxMessageId;
                    }
                    break;
                }
                case TdApi.UpdateChatUnreadMentionCount.CONSTRUCTOR: {
                    TdApi.UpdateChatUnreadMentionCount updateChat = (TdApi.UpdateChatUnreadMentionCount) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.unreadMentionCount = updateChat.unreadMentionCount;
                    }
                    break;
                }
                case TdApi.UpdateMessageMentionRead.CONSTRUCTOR: {
                    TdApi.UpdateMessageMentionRead updateChat = (TdApi.UpdateMessageMentionRead) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.unreadMentionCount = updateChat.unreadMentionCount;
                    }
                    break;
                }
                case TdApi.UpdateChatReplyMarkup.CONSTRUCTOR: {
                    TdApi.UpdateChatReplyMarkup updateChat = (TdApi.UpdateChatReplyMarkup) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.replyMarkupMessageId = updateChat.replyMarkupMessageId;
                    }
                    break;
                }
                case TdApi.UpdateChatDraftMessage.CONSTRUCTOR: {
                    TdApi.UpdateChatDraftMessage updateChat = (TdApi.UpdateChatDraftMessage) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.draftMessage = updateChat.draftMessage;
                        setChatPositions(chat, updateChat.positions);
                    }
                    break;
                }
                case TdApi.UpdateChatPermissions.CONSTRUCTOR: {
                    TdApi.UpdateChatPermissions update = (TdApi.UpdateChatPermissions) object;
                    TdApi.Chat chat = chats.get(update.chatId);
                    synchronized (chat) {
                        chat.permissions = update.permissions;
                    }
                    break;
                }
                case TdApi.UpdateChatNotificationSettings.CONSTRUCTOR: {
                    TdApi.UpdateChatNotificationSettings update = (TdApi.UpdateChatNotificationSettings) object;
                    TdApi.Chat chat = chats.get(update.chatId);
                    synchronized (chat) {
                        chat.notificationSettings = update.notificationSettings;
                    }
                    break;
                }
                case TdApi.UpdateChatDefaultDisableNotification.CONSTRUCTOR: {
                    TdApi.UpdateChatDefaultDisableNotification update = (TdApi.UpdateChatDefaultDisableNotification) object;
                    TdApi.Chat chat = chats.get(update.chatId);
                    synchronized (chat) {
                        chat.defaultDisableNotification = update.defaultDisableNotification;
                    }
                    break;
                }
                case TdApi.UpdateChatIsMarkedAsUnread.CONSTRUCTOR: {
                    TdApi.UpdateChatIsMarkedAsUnread update = (TdApi.UpdateChatIsMarkedAsUnread) object;
                    TdApi.Chat chat = chats.get(update.chatId);
                    synchronized (chat) {
                        chat.isMarkedAsUnread = update.isMarkedAsUnread;
                    }
                    break;
                }
                case TdApi.UpdateChatIsBlocked.CONSTRUCTOR: {
                    TdApi.UpdateChatIsBlocked update = (TdApi.UpdateChatIsBlocked) object;
                    TdApi.Chat chat = chats.get(update.chatId);
                    synchronized (chat) {
                        chat.isBlocked = update.isBlocked;
                    }
                    break;
                }
                case TdApi.UpdateChatHasScheduledMessages.CONSTRUCTOR: {
                    TdApi.UpdateChatHasScheduledMessages update = (TdApi.UpdateChatHasScheduledMessages) object;
                    TdApi.Chat chat = chats.get(update.chatId);
                    synchronized (chat) {
                        chat.hasScheduledMessages = update.hasScheduledMessages;
                    }
                    break;
                }

                case TdApi.UpdateUserFullInfo.CONSTRUCTOR:
                    TdApi.UpdateUserFullInfo updateUserFullInfo = (TdApi.UpdateUserFullInfo) object;
                    usersFullInfo.put(updateUserFullInfo.userId, updateUserFullInfo.userFullInfo);
                    break;
                case TdApi.UpdateBasicGroupFullInfo.CONSTRUCTOR:
                    TdApi.UpdateBasicGroupFullInfo updateBasicGroupFullInfo = (TdApi.UpdateBasicGroupFullInfo) object;
                    basicGroupsFullInfo.put(updateBasicGroupFullInfo.basicGroupId, updateBasicGroupFullInfo.basicGroupFullInfo);
                    break;
                case TdApi.UpdateSupergroupFullInfo.CONSTRUCTOR:
                    TdApi.UpdateSupergroupFullInfo updateSupergroupFullInfo = (TdApi.UpdateSupergroupFullInfo) object;
                    supergroupsFullInfo.put(updateSupergroupFullInfo.supergroupId, updateSupergroupFullInfo.supergroupFullInfo);
                    break;
                default:
                    // print("Unsupported update:" + newLine + object);
            }
        }
    }

    private static class AuthorizationRequestHandler implements Client.ResultHandler {
        @Override
        public void onResult(TdApi.Object object) {
            switch (object.getConstructor()) {
                case TdApi.Error.CONSTRUCTOR:
                    System.err.println("Receive an error:" + newLine + object);
                    onAuthorizationStateUpdated(null); // repeat last action
                    break;
                case TdApi.Ok.CONSTRUCTOR:
                    // result is already received through UpdateAuthorizationState, nothing to do
                    break;
                default:
                    System.err.println("Receive wrong response from TDLib:" + newLine + object);
            }
        }
    }

    public static class UnresolvedRecord {

        public final String reason;
        public final String description;
        public final String channelId;
        public final List<String> messageIds;

        private UnresolvedRecord(String reason, String description, String channelId, List<String> messageIds) {
            this.reason = reason;
            this.description = description;
            this.channelId = channelId;
            this.messageIds = messageIds;
        }

        public static UnresolvedRecord from(String[] columns) {
            return new UnresolvedRecord(
                columns[0],
                columns[1],
                columns[2],
                (columns.length == 3) ? Collections.emptyList() : List.of(Arrays.copyOfRange(columns, 3, columns.length))
            );
        }

        @Override
        public String toString() {
            return reason +
                ";" +
                description +
                ";" +
                channelId +
                ";" + String.join(";", messageIds);
        }
    }

    public static class UnresolvedChannelRecord {

        public final String description;
        public final String channelId;

        private UnresolvedChannelRecord(String description, String channelId) {
            this.description = description;
            this.channelId = channelId;
        }

        public static UnresolvedChannelRecord from(String[] columns) {
            return new UnresolvedChannelRecord(
                columns[0],
                columns[1]
            );
        }

        @Override
        public String toString() {
            return description +
                ";" +
                channelId;
        }
    }

    public static class ReportMessageResolver {

        public static void resolveIds(Path path) {
            try (
                BufferedReader br = Files.newBufferedReader(path);
                BufferedWriter bw = Files.newBufferedWriter(path.getParent().resolve("resolved_" + path.getFileName()))) {

                // CSV file delimiter
                String DELIMITER = ";";

                // read the file line by line
                String line;
                boolean isFirstLine = true;
                while ((line = br.readLine()) != null) {
                    // convert line into columns
                    UnresolvedRecord unresolvedRecord = UnresolvedRecord.from(line.split(DELIMITER));
                    CountDownLatch latch = new CountDownLatch(unresolvedRecord.messageIds.size());
                    LinkedBlockingQueue<Long> chatIds = new LinkedBlockingQueue<>(unresolvedRecord.messageIds.size());
                    LinkedBlockingQueue<Long> messageIds = new LinkedBlockingQueue<>(unresolvedRecord.messageIds.size());
                    for (String messageId : unresolvedRecord.messageIds) {
                        client.send(
                            new TdApi.GetMessageLinkInfo("https://t.me/" + unresolvedRecord.channelId + "/" + messageId),
                            object -> {
                                TdApi.MessageLinkInfo info = (TdApi.MessageLinkInfo) object;
                                messageIds.add(info.message.id);
                                chatIds.add(info.message.chatId);
                                latch.countDown();
                            },
                            e -> latch.countDown());
                    }
                    latch.await();
                    if (chatIds.isEmpty() || messageIds.isEmpty()) {
                        continue;
                    }
                    if (!isFirstLine) {
                        bw.write("\n");
                    }
                    isFirstLine = false;
                    bw.write(ResolvedRecord.from(unresolvedRecord, chatIds.poll(), messageIds).toString());
                }
                bw.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static class ResolvedRecord {

        public final String reason;
        public final String description;
        public final Long channelId;
        public final Collection<Long> messageIds;

        private ResolvedRecord(String reason, String description, Long channelId, Collection<Long> messageIds) {
            this.reason = reason;
            this.description = description;
            this.channelId = channelId;
            this.messageIds = messageIds;
        }

        public static ResolvedRecord from(String[] columns) {
            return new ResolvedRecord(
                columns[0],
                columns[1],
                Long.parseLong(columns[2]),
                List.of(Arrays.copyOfRange(columns, 3, columns.length))
                    .stream()
                    .mapToLong(Long::parseLong)
                    .boxed()
                    .collect(Collectors.toUnmodifiableList())
            );
        }

        public static ResolvedRecord from(UnresolvedRecord record, Long channelId, Collection<Long> messageIds) {
            return new ResolvedRecord(record.reason, record.description, channelId, messageIds);
        }

        @Override
        public String toString() {
            return reason +
                ";" +
                description +
                ";" +
                channelId +
                ";" +
                messageIds.stream().map(Object::toString).collect(Collectors.joining(";"));
        }
    }

    public static class ReportMessageExecutor {

        public static void execute(Path path) {
            List<ResolvedRecord> records = readAllRecords(path);
            CountDownLatch latch = new CountDownLatch(records.size());
            print("\n\n[ACTION] Reporting message");
            for (ResolvedRecord record : records) {
                reportMessages(record, latch);
            }
            try {
                latch.await();
                print("\n\n[ACTION] End of Reporting message");
            } catch (InterruptedException e) {
                throw new IllegalStateException("[ERROR] Concurrent problem", e);
            }
        }

        private static List<ResolvedRecord> readAllRecords(Path path) {
            List<ResolvedRecord> records = new ArrayList<>();
            try (BufferedReader br = Files.newBufferedReader(path)) {
                // CSV file delimiter
                String DELIMITER = ";";

                // read the file line by line
                String line;
                while ((line = br.readLine()) != null) {
                    // convert line into columns
                    records.add(ResolvedRecord.from(line.split(DELIMITER)));
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return records;
        }

        private static void reportMessages(ResolvedRecord record, CountDownLatch latch) {
            client.send(
                new TdApi.ReportChat(
                    record.channelId,
                    record.messageIds.stream().mapToLong(Long::longValue).toArray(),
                    toReason(record.reason),
                    record.description
                ),
                reportingResult -> {
                    print(reportingResult.toString());
                    print("[REPORTED] " + record.toString());
                    latch.countDown();
                },
                e -> {
                    print("[ERROR] Fail to report " + record.toString());
                    latch.countDown();
                });
        }

        private static TdApi.ChatReportReason toReason(String reason) {
            switch (reason) {
                case "unrelated":
                    return new TdApi.ChatReportReasonUnrelatedLocation();
                case "custom":
                    return new TdApi.ChatReportReasonCustom();
                case "childAbuse":
                    return new TdApi.ChatReportReasonChildAbuse();
                case "fake":
                    return new TdApi.ChatReportReasonFake();
                case "spam":
                    return new TdApi.ChatReportReasonSpam();
                case "violence":
                    return new TdApi.ChatReportReasonViolence();
                case "copyright":
                    return new TdApi.ChatReportReasonCopyright();
                case "pornography":
                    return new TdApi.ChatReportReasonPornography();
                default:
                    throw new IllegalStateException("Unsupported type");
            }
        }
    }

    public static class ReportChannelExecutor {

        public static void execute(Path path) {
            List<UnresolvedChannelRecord> records = readAllRecords(path);
            CountDownLatch latch = new CountDownLatch(records.size());
            print("\n\n[ACTION] Reporting channels");
            for (UnresolvedChannelRecord record : records) {
                reportChannel(record, latch);
            }
            try {
                latch.await();
                print("[ACTION] End of Reporting channels\n\n");
            } catch (InterruptedException e) {
                throw new IllegalArgumentException("[ERROR] Concurrent problem", e);
            }
        }

        private static List<UnresolvedChannelRecord> readAllRecords(Path path) {
            List<UnresolvedChannelRecord> records = new ArrayList<>();
            try (BufferedReader br = Files.newBufferedReader(path)) {
                // CSV file delimiter
                String DELIMITER = ";";

                // read the file line by line
                String line;
                while ((line = br.readLine()) != null) {
                    // convert line into columns
                    records.add(UnresolvedChannelRecord.from(line.split(DELIMITER)));
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return records;
        }

        private static void reportChannel(UnresolvedChannelRecord record, CountDownLatch latch) {
            client.send(new TdApi.SearchPublicChat(record.channelId),
                data -> {
                    TdApi.Chat chat = (TdApi.Chat) data;
                    client.send(
                        new TdApi.ReportChat(
                            chat.id,
                            new long[]{},
                            new TdApi.ChatReportReasonCustom(),
                            record.description
                        ),
                        reportingResult -> {
                            print(reportingResult.toString());
                            print("[REPORTED] " + record.toString());
                            latch.countDown();
                        },
                        e -> {
                            print("[ERROR] Failed to report " + record.toString());
                            latch.countDown();
                        });
                },
                e -> {
                    print("[ERROR] Failed to report " + record.toString());
                    latch.countDown();
                });
        }
    }
}
