# Overview
Tool is used for submitting violation reports about spam, fake, pornography, child abuse
messages posted in the telegram channel.

Each violation report could contain information about multiple telegram channels.
Such structure allows us to submit numerous violations very quickly.

**Tool have 2 options** for submitting reports:
- submit report per telegram message (use action `reportMessages`)
- submit report per telegram message (use action `reportChannels`)


**Message report** has a semi-csv structure and represented by following data.<br>
Columns: type of report, description, channel name, message id from the link 
```
spam;Fake news;end_of_world;10;12
violence;Supporting terrorism;army_of_mordor;2101
fake;Fake news;end_of_world;2103
```

Legend info:
-   type of report: unrelated, custom, childAbuse, fake, spam, violence, copyright, pornography
-   description: general information about inappropriate content
-   channel name: telegram`s channel name
-   message id - message offset in telegram channel

**Channel report** has a semi-csv structure and represented by following data.<br>
Columns: description, channel name

```
Fake news;end_of_world
Supporting terrorism;army_of_mordor
```

Legend info:
-   description: general information about inappropriate content
-   channel name: telegram`s channel name

# How to get information for message report:

- Choose inappropriate message in the telegram channel and click on button `Copy Message Link`. 
- Extract id of channel,message from url and add to the file
- Place data into semi-csv file

For example: 
`Copy Message Link` returns following link https://t.me/end_of_world/10.
Extracted values are: 
- Channel name: end_of_world
- Message Id: 10

Sturcture of the file:
```
spam,Fake news;end_of_world;10
```

# How to get information for channel report:
- Choose any inappropriate message in the telegram channel and click on button `Copy Message Link`.
- Extract id of channel
- Place data into semi-csv file

For example:
`Copy Message Link` returns following link https://t.me/end_of_world/10.
Extracted values are:
- Channel name: end_of_world

Sturcture of the file:
```
Fake news;end_of_world
```

# How to submit report per telegram channel without marking any messages:

- Find telegram channel with inappropriate content
- Place telegram channel into semi-csv file 
```
custom;Fake news;end_of_world
```


# 1. Prerequisites

1. Install [Docker Desktop](https://docs.docker.com/desktop/windows/install/)
2. Create folder for storing reports. For instance: `/Users/plm/reports`

# 2. Build tb_reporter container

## 2.1.1 Using local image

1. Change Docker Desktop setting: Prefferences -> Resources -> Advance

        CPUs: 4
        Memory: 4GB

2. Build docker image

        docker build . --tag plm34869/tg_reporter:latest

## 2.1.2 Using image from hub.docker.com

1. Pull plm34869/tg_reporter:latest from hub.docker.com

      docker pull plm34869/tg_reporter:latest

## 2.2 Create container `tg_reporter` with launched mounted report folder from local machine

      #replace /Users/plm/reports by your local folder 
      docker create --name tg_reporter -it --mount src=/Users/plm/reports,target=/.td_app/tdlib/bin/reports,type=bind plm34869/tg_reporter:latest

## 2.3 Launch application & make firs login.

      docker start -ia tg_reporter
You will need to enter phone number and verification code from telegram message for obtaining access.

# 3. Prepare reports.
- Open telegram channel. For instance: https://t.me/end_of_world
- Find all inappropriate messages and put them into file `report_end_of_world.csv`
- Resolve ids for messages, because offsets could be changed in the channel.
  As a result you will get a file `resolved_report_end_of_world.csv`
   
      docker start -ia tg_reporter
      resolveIds /.td_app/tdlib/bin/reports/report_end_of_world.csv
      q
- Share `resolved_report_end_of_world.csv` file with other people and ask them to submit report.
  `resolved_report_end_of_world.csv` could be found in your local folder for reports.
- Submit `resolved_report_end_of_world.csv` report.

      docker start -ia tg_reporter
      report /.td_app/tdlib/bin/reports/resolved_report_end_of_world.csv
