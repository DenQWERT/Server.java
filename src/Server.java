//package server;
// Верисия V3.20 от 29.11.2020 года от SDA

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.io.File;




public class Server {
    static ArrayList<Socket> clients = new ArrayList<>();
    static ArrayList<String> userNames = new ArrayList<>();
    static ArrayList<Integer> userID = new ArrayList<>();
    static ArrayList<String> nameID = new ArrayList<>();
    //static ArrayList<String> old10Messages = new ArrayList<>();
    static final int N10=100; // - число старых сообщений выводимых новому пользователю в при подключении к чату в териминал
    static String [] old10Messages = new String[N10];
    static int Sh = 2;  // Sh = 2 - шифруем  Sh=0 не шифруем
//===================== Обьекты для Сеарилизации ===================================================
    // Идея стырена отсюда => https://habr.com/ru/post/431524/

    public Server() throws IOException {
    }
//===================== Обьекты для Сеарилизации ===================================================
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        new Pack();

        // - Массиву 10 последних сообщений приваиваем пустые значения
        IntStream.range(0, (N10 - 0)).forEach(i -> old10Messages[i] = "");


        File file = new File("client_messages.out");
        if (file.exists()&&(file.length()>0)) {
            ObjectInputStream inFile;
            inFile = new ObjectInputStream(new FileInputStream(file));
            old10Messages = (String[]) inFile.readObject();
            inFile.close();
        }
        else {file.createNewFile();}

        for (int i = 0; i < N10; i++) {
            if (old10Messages[i].length()>0) { System.out.println("Выводим сериализованные сообщения из архива old10Messages[" + i + "]=" + old10Messages[i]); }
        }
//========================================================================

        try {
            ServerSocket serverSocket = new ServerSocket(8188);
            System.out.println("Сервер запущен");
            while (true) {
                Socket socket = serverSocket.accept();
                clients.add(socket);
                int id=userID.size()+1;
                userID.add(id);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                System.out.println("Клиент № " + id + " подключился");
                Thread ServerToClientMessagesThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String userName = null;
                        try {
                            String strTemp = "01/" + userID.get(id-1).toString() + "/Wed Jan 01 12:00:00 GMT+00:00 2020/Сервер/01/6/7/  Вас приветствует сервер Чата. Я версия сервера - V3.20 от 29.11.2020./45";
                            out.writeUTF(Pack.paked(strTemp,Sh));
                            Date data = new Date();
                            data.getTime();
                            strTemp = "01/" + userID.get(id-1).toString() +"/"+ data.toString() +"/" + "Сервер/01/6/7/  Введите свое имя:/45";
                            out.writeUTF(Pack.paked(strTemp,Sh));
                            System.out.println(strTemp);
                            //old10Messages.add(strTemp);
                            Protocol P = new Protocol();
                            String s = in.readUTF();
                            System.out.println("Принята от клиента зашифрованная строка =" + s);
                            s = Pack.unpaked(s,Sh);
                            System.out.println("Расшифрованная строка от клиента =" + s);
                            P.RazborProtocol(s);

                            userName = P.name;
                            System.out.println("Имя регистрирующегося пользователя = " + P.name);
                            System.out.println("Цвет регистрирующегося пользователя = " + P.color);

                            strTemp = "01/" + userID.get(id-1).toString() +"/"+ data.toString() +"/" + "Сервер/01/6/7/  Ваше имя - " + P.name + "/45";
                            out.writeUTF(Pack.paked(strTemp,Sh));

                            userNames.add(userName);
                            nameID.add(userName);

                            //System.out.println("Клиент " + userName + " подключился к чату");
                            if (P.type!=15) { // - Отправляем старые сообщения только при первичном подключении. При вторичных восстановлениях соединени ниче не отправляем.
                                for (int i = (N10 - 10); i <= N10 - 1; i++) {
                                    if (old10Messages[i].length() > 0) {
                                        out.writeUTF(Pack.paked(old10Messages[i], Sh));
                                        TimeUnit.MILLISECONDS.sleep(10);
                                        System.out.println("Отправляем пользователю " + i + " " + old10Messages[i]);
                                    }
                                    //System.out.println("НЕ Отправляем пользователю " + i + " " + old10Messages[i]);
                                }
                            }
                            if (P.type!=15) { // - Отправить всем сообщение о подключении нового клиента,
                                              // если это не повторное соединение.
                                strTemp = "" + userName + " в чате";
                                broadcastMsg(strTemp, "Сервер:", userID.get(id - 1), P.color);
                            }
                            while (true) {
                                Protocol P1 = new Protocol();
                                P1.RazborProtocol(Pack.unpaked(in.readUTF(),Sh));
                                String str = P1.message;
                                System.out.println(userName + " прислал сообщение: " + str + " Тип сообщения = " + P1.type);// отсюда надо удалить все что до ##
                                switch (P1.type){ //=== -  Разборщик сообщений клиентов ===================================
                                    case 04: // - Клиент сам отключается от сервера.
                                        System.out.println("Клиент сам отключеется - " + P1.message);
                                        clients.remove(socket);
                                        userNames.remove(userName);
                                        try {     socket.close();
                                        } catch (IOException ioException) {
                                            ioException.printStackTrace();
                                        }
                                        break;
                                    case 15 : // ======= Запрос на повторное воссоединение клиента с сервером====================
                                        System.out.println("Принят запрос на повторное соединение от клиента P1.message = " + P1.message + "P.name =  " + P.name);
                                        break;
                                    case 02 :  // ====== Приняли от клиента обычное сообщение и рассылаем его на всех остальных клиентов
                                        userName =  P.name;
                                        broadcastMsg(str, userName, userID.get(id-1), P1.color);
                                        break;
                                    case 13 : // ==== Засылаем клиенту сколько он попросил сообщений ( CountMessages )
                                        int CountMessages = Integer.parseInt(P1.message);
                                        CountMessages = (CountMessages>N10)?N10:CountMessages;
                                        //out.writeUTF(Pack.paked("Последние " + CountMessages + " сообщений:", Sh));
                                        for (int i=0; i<=N10-1;i++){
                                            if ((i>=(N10-2-CountMessages))&&(old10Messages[i].length()>0)) {
                                                out.writeUTF(Pack.paked(old10Messages[i], Sh));
                                                TimeUnit.MILLISECONDS.sleep(10);
                                                System.out.println("Отправляем пользователю "+ P.name + " запрошенный архив "+ i + " " + old10Messages[i]);
                                            }
                                            //toFile.writeObject(old10Messages);
                                            //toFile.close();
                                            //System.out.println("НЕ Отправляем пользователю " + i + " " + old10Messages[i]);
                                        }
                                        break;
                                    case 11 : // - Выдаем клиенту список кто сейчас в чате
                                        String spisokAll="Сейчас в чате онлайн:";
                                        for (String name : userNames) {
                                            spisokAll = spisokAll + " " + name;
                                        }
                                        //Date data = new Date();
                                        data.getTime();
                                        out.writeUTF(Pack.paked("03/" + userID.get(id-1).toString() +"/"+ data.toString() +"/" + userName + "/02/6/7/" + spisokAll +"/11", Sh));
                                        System.out.println("Список запрошенных пользователей " + spisokAll);
                                        break;
                                    case 14: // - Выдаем клиенту список всех кто был в чате
                                        spisokAll="Список всех кто был в чате: ";
                                        /*for (String name : nameID) {
                                            spisokAll = spisokAll + " " + name;
                                        }
                                        data.getTime();
                                        out.writeUTF(Pack.paked("03/" + userID.get(id-1).toString() +"/"+ data.toString() +"/" + userName + "/02/6/7/" + spisokAll +"/11", Sh));
                                        System.out.println("Список всех кто был в чате " + spisokAll);*/
                                        for (int i=0; i<=N10-1;i++){
                                            if (old10Messages[i].length()>0) {
                                                Protocol P2 = new Protocol();
                                                P2.RazborProtocol(old10Messages[i]);
                                                if ((spisokAll.indexOf(P2.name)<0)&&(P2.name.compareTo("Сервер")!=1)&&(P2.name.compareTo("Сервер:")!=1)) // - Если текущего имени в списке еще нет, то добавить его к списку
                                                {   String sp2name =  P2.name;
                                                    sp2name = sp2name.replace(",",""); // - удаляем случайные запятые(служебный символ) в именах
                                                    sp2name = sp2name.replace("#",""); // - удаляем случайные #(служебный символ) в именах
                                                    sp2name = sp2name.replace(":","");
                                                    //sp2name = sp2name.replace("Сервер","");
                                                    spisokAll = spisokAll  + "  " + P2.idUser + "-#-" + sp2name + ", ";}
                                            }
                                        }
                                        spisokAll = spisokAll.substring(0, spisokAll.length() - 2);
                                        spisokAll = spisokAll+",";
                                        data.getTime();
                                        out.writeUTF(Pack.paked("14/" + userID.get(id-1).toString() +"/"+ data.toString() +"/" + userName + "/01/6/7/" + spisokAll +"/11", Sh));
                                        System.out.println("Список запрошенных пользователей " + spisokAll);
                                        break;

                                }
                            }
                        } catch (IOException | ParseException | InterruptedException e) {
                            if (userName.length()>0) {
                                ////// broadcastMsg(/*"Клиент " + userName + */" отключился", userName, userID.get(id - 1));
                                int a = 5;
                            }
                            System.out.println("Клиент " + userName + " отключился"); // - сам отключился
                            try {
                                clients.remove(socket);
                                userNames.remove(userName);
                                socket.close();
                            } catch (IOException exception) {
                                exception.printStackTrace();
                            }

                        } finally {
                            try {
                                clients.remove(socket);
                                userNames.remove(userName);
                                socket.close();

                                ///// broadcastMsg("Кл. " + userName + " отключился", "Сервер", userID.get(id-1));
                            } catch (IOException exception) {
                                exception.printStackTrace();
                            }
                        }
                    //} catch (InterruptedException e) {
                    //        e.printStackTrace();
                    //    } catch (ParseException e) {
                    //        e.printStackTrace();
                    //    } catch (IOException e) {
                    //        e.printStackTrace();
                        }
                    });
                ServerToClientMessagesThread.start();
            }
        } catch (IOException ex) {
            System.out.println("Нет подключенных клиентов");
        }
    }

    public static void broadcastMsg(String str, String userName, int id, int userColor) throws IOException {
        DataOutputStream out;
        for (Socket socket : clients) {
            out = new DataOutputStream(socket.getOutputStream());
            //String stringOut = userNames + "##" + userName + ": " + str;
            String stringOut = str;
            Date data = new Date();
            data.getTime();
            stringOut = "03/" + userID.get(id-1).toString() +"/"+ data.toString() +"/" + userName + "/" + userColor + "/6/7/" + stringOut +"/60";
            System.out.println(stringOut);

            //System.out.println("До    " + Arrays.toString(old10Messages));
            if (old10Messages[N10-1].compareTo(stringOut.toString())!=0) { // - Повторно те же строки не записываем в архив от других клиентов
                for (int i = 0; i < N10 - 1; i++) {
                    old10Messages[i] = old10Messages[i + 1];
                }// - смещаем все сообщения влево на одно
                old10Messages[N10 - 1] = stringOut.toString();// - добавляем справа в последнюю позицию текущее поледнее сообщение
                ObjectOutputStream toFile;
                toFile = new ObjectOutputStream( new FileOutputStream("client_messages.out"));
                toFile.writeObject(old10Messages);
                toFile.close();
            }
            //System.out.println("После" + Arrays.toString(old10Messages));
            //old10Messages.add(stringOut);
            //stringOut = Pack.paked(stringOut, 2);
            out.writeUTF(Pack.paked(stringOut, Sh));

        }
    }

}
