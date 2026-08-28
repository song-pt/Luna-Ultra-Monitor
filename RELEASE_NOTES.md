# Luna Monitor 1.0.0 / Public v1

## 中文

- 修复变焦提交时额外回读导致部分固件暂停图传的问题：滑块松手后只发送一次合并后的变焦写入；
- 参数页只保留视频参数，左侧画质、右侧对应帧率；
- 增加独立双 LUT 页面，支持一个还原 LUT 和一个风格 LUT，也支持单独使用任意一个；
- 增加中文、English、日本語界面；
- 增加 Apache-2.0、来源记录、第三方声明和侵删声明。

## English

- Avoids the extra zoom read-back that paused live view on some firmware; one coalesced zoom command is sent after release.
- Video-only format panel with resolution on the left and supported frame rates on the right.
- Separate dual-LUT panel with optional recovery and look `.cube` files.
- Chinese, English, and Japanese UI.
- Apache-2.0 license, provenance, third-party notices, and rights-holder contact statement.

## 日本語

- 一部ファームウェアでライブビューを停止させたズーム直後の追加読戻しを廃止し、操作終了時に1回だけ送信します。
- 左側に解像度、右側に対応フレームレートを表示する動画専用設定画面。
- 変換 LUT とルック LUT を任意に使える独立デュアル LUT 画面。
- 中国語、英語、日本語 UI。
- Apache-2.0、出典記録、第三者通知、権利者向け連絡声明を追加。
