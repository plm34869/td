Reporting example:

1. Prepare csv file for reporting telegram messages as spam, violence, etc   
Create file report_channel_name.csv with following content

```
spam,Markerting advertisment,online-market,10,11
fake,Photoshop,maps,12
```

Legend info: 
-   type of report - unrelated, custom, childAbuse, fake, spam, violence, copyright, pornography 
-   description - general information about content
-   channel name - telegram channel name
-   message link id - Choose message and click on button `Copy Message Link`. Extract id of channel,message from url and add to the file

Example:
Link: https://t.me/onlineshop/102
<- Parsed data ->
Channel name: onlineshop
Message if: 102

<-Result->
spam,Advertisement,onlineshop,102

 2. Build Java bindings for example - https://tdlib.github.io/td/build.html?language=Java

- Choose a programming language - Java
- Choose an operating system (installed OS on your computer)
- Execute steps in the documentation

 3. During first execution of library, it will ask phone number and verification code for obtaining access token from telegram.

<img width="515" alt="Screenshot 2022-03-02 at 11 27 19" src="https://user-images.githubusercontent.com/14370349/156362523-2e94ba30-8b0f-4d04-b656-5d812b2fdc47.png">


Example: 
cd $HOME/td/tdlib/bin
/usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home/bin/java '-Djava.library.path=.' org/drinkless/tdlib/example/Example

 4. Resolve human readable telegram data as telegram ids via cli command . Command will create a new file in the same directory with preffix `resolved_....`
 
 resolveIds ~/report_channel_name.csv  
 
 5. Report telegram messages for resolved telegram data ~/report_channel_name.resolved.csv
report ~/resolved_report_channel_name.csv
