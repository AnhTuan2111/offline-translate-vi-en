# Nhật ký thay đổi

Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.1.0/),
đánh số theo [Semantic Versioning](https://semver.org/lang/vi/).

Mỗi bản phát hành có **hai gói**, cùng một mã nguồn:

| Gói | Có gì | Kích thước |
|---|---|---|
| `TuDienOffline-<ver>.msi` | Từ điển + dịch câu bằng luật. **Không có mô hình AI nào.** | ~46 MB |
| `TuDienOffline-AI-<ver>.msi` | Kèm mô hình nơ-ron dịch câu chạy cục bộ | ~185 MB |

Cả hai đều chạy **hoàn toàn offline** — không có một dòng mã nào mở kết nối mạng.

---

## [Chưa phát hành]

## [0.1.0] — 2026-09-25

Bản đầu tiên dùng được.

### Tra cứu
- Tra từ Anh→Việt: 108.862 mục từ, 121.586 khoá (gồm cả cụm động từ như `give up`
  vốn không phải mục từ riêng trong nguồn). Tra một từ **13,7 µs**.
- Tra cụm thì đẩy thẻ cụm lên đầu, không bắt người dùng tự tìm giữa mục từ dài.
- Tìm Việt→Anh xếp theo độ liên quan; gõ **không dấu** vẫn ra đúng kết quả.
- Gõ sai chính tả: tiếng Anh đoán bằng trigram, tiếng Việt gợi ý "Ý bạn là…?".

### Dịch câu — ba mức
- **Chú giải theo cụm**: mỗi cụm một ô, bấm để đổi nghĩa.
- **Dịch bằng luật**: chọn nghĩa theo từ loại, sắp lại trật tự tiếng Việt, thêm dấu hiệu
  thì. Kèm bảng xác suất dịch từ học từ 1,2 triệu cặp câu song ngữ để chọn đúng nghĩa
  (`government` → "chính phủ" chứ không phải "sự cai trị"). **2 ms** mỗi câu.
- **Dịch bằng mô hình nơ-ron** *(chỉ có trong gói AI)*: opus-mt-en-vi chạy cục bộ,
  **150–450 ms** mỗi câu. Giao diện ghi rõ đây là AI chạy trên máy người dùng.

### Nguồn từ điển
- Thêm được nguồn khác dưới dạng bảng TSV hai cột; bật/tắt và đổi thứ tự ưu tiên ngay
  trong ứng dụng, không phải sinh lại dữ liệu.

### Đóng gói
- Bộ cài `.msi` cho Windows, không cần cài sẵn Java.
- RAM khi chạy 132–170 MB.

### Ghi chú kỹ thuật
- Dữ liệu 16,4 MB: pack nén theo khối + chỉ mục ngược tự viết, không dùng SQLite hay Lucene.
- Toàn bộ `dict-core` không có một dependency nào ngoài JDK.
- 110 test + 37 tiêu chí nghiệm thu tự động trên dữ liệu thật.

### Đã bỏ giữa chừng *(ghi lại để khỏi làm lại)*
- **Tra nhanh từ clipboard + biểu tượng khay**: làm xong rồi gỡ theo quyết định của chủ dự án.
- **Nhét từ ghép tiếng Việt vào chỉ mục**: làm xong, đo, thấy chỉ mục phình 47% mà thứ tự
  kết quả gần như không đổi → bỏ. Danh sách từ ghép chuyển sang dùng cho gợi ý chính tả.

[Chưa phát hành]: https://github.com/AnhTuan2111/offline-translate-vi-en/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/AnhTuan2111/offline-translate-vi-en/releases/tag/v0.1.0
