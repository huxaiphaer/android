package com.owncloud.android.operations.common

enum class OperationType {
    // SHARES
    GET_SHARES,
    GET_SHAREES,
    GET_CAPABILITIES,
    CREATE_SHARE_WITH_SHAREES,
    CREATE_PUBLIC_SHARE,
    UPDATE_SHARE,
    REMOVE_SHARE,

    // FILES
    UPLOAD_FILE,
    DOWNLOAD_FILE,
    REMOVE_FILE,
    RENAME_FILE,
    CREATE_FOLDER,
    MOVE_FILE,
    COPY_FILE,
    SYNCHRONIZE_FOLDER
}
