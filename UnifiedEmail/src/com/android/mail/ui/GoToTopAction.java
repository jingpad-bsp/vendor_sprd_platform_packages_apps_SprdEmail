package com.android.mail.ui;

public class GoToTopAction {
    public interface GoToTopListener {
        public void showOrDismissTopButton(int mode, GoToTopCallback callback);
    }

    public interface GoToTopCallback {
        public void performGoToTop();
    }
}
