# SimpleSmartCtl



## \[English]



### Overview

An Android app to check the health status of external HDDs and SSDs via USB OTG. It reads S.M.A.R.T. data using SCSI ATA PASS-THROUGH commands without requiring Root access.



### Features

- No Root required.

- Simple connection via USB OTG.

- Health scoring system (Rank S, A, B, C) based on critical attributes like bad sectors.

- Displays raw S.M.A.R.T. attribute data.

- Switchable interface language (English / Japanese).

- Copy analysis results to clipboard.



### How to use

1. Launch the app.

2. Connect your HDD/SSD to your phone using a USB OTG cable/adapter.

&nbsp;  (Note: Some devices require enabling "OTG Connection" in system settings.)

3. Tap "Get S.M.A.R.T. Info".

4. Grant USB permission when prompted.

5. After data retrieval, tap "Show Analysis" to view the health score and details.

6. Tap "Disconnect" before unplugging the cable.



### Requirements

- Android device with USB Host support.

- USB OTG cable.

- External power supply is mandatory for 3.5-inch HDDs (smartphone power is insufficient).



### Disclaimer

- This app relies on the SAT (SCSI / ATA Translation) standard. It will not work with USB adapters that do not support SAT.

- Use at your own risk. The developer is not responsible for any data loss or hardware damage.



---



## [Japanese]



### 概要

USB OTG経由でスマートフォンに接続した外付けHDDやSSDのS.M.A.R.T.情報を読み取り、健康状態を診断するAndroidアプリです。Root権限は不要です。



### 特徴

- Root化不要。

- USBケーブルで接続するだけのシンプル操作。

- 不良セクタ数などの重要項目に基づき、健康状態をS～Cのランクで評価。

- S.M.A.R.T.の生データ（Raw値）の確認が可能。

- 英語・日本語の言語切り替え機能。

- 解析結果のクリップボードコピー機能。



### 使い方

1. アプリを起動します。

2. USB OTGケーブルを使用してHDD/SSDを接続します。

&nbsp;  （注意：機種によっては設定画面で「OTG接続」を手動でONにする必要があります）

3. 「S.M.A.R.T. 情報を取得」ボタンをタップします。

4. USBアクセスの権限を求められたら許可してください。

5. 取得成功後、「解析結果を表示」ボタンをタップすると診断結果が表示されます。

6. ケーブルを抜く際は、事前に「切断」ボタンをタップしてください。



### 動作要件

- USBホスト機能を搭載したAndroid端末。

- USB OTGケーブルまたは変換アダプタ。

- 3.5インチHDDを使用する場合は、必ず外部電源（ACアダプタ）が必要です。スマホからの給電だけでは動作しません。



### 免責事項・注意点

- 本アプリはSAT (SCSI / ATA Translation) 規格を利用しています。SAT非対応のUSB変換アダプタや古いチップセットでは動作しない場合があります。

- 本アプリの使用により発生したいかなる損害やデータ消失についても、開発者は責任を負いません。自己責任でご使用ください。

