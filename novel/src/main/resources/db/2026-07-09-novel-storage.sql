alter table novels
    modify column file_path varchar(255) not null comment '小说保存路径或对象存储key',
    add column storage_type varchar(20) not null default 'LOCAL' comment '文件存储类型：LOCAL/COS' after file_path,
    add column object_key varchar(255) null comment '对象存储key，LOCAL时与file_path一致' after storage_type,
    add column original_filename varchar(255) null comment '上传原始文件名' after object_key,
    add column file_size bigint null comment '文件大小，单位字节' after original_filename;

update novels
set object_key = file_path
where object_key is null;

