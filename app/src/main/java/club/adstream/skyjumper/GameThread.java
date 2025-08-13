package club.adstream.skyjumper;

class GameThread extends Thread {
    private static final double DT = 1.0 / 60.0;
    private final GameView view;
    private volatile boolean running = true;

    GameThread(GameView v) { this.view = v; }
    public void requestStop() { running = false; interrupt(); }

    @Override public void run() {
        long prev = System.nanoTime();
        double acc = 0;
        while (running) {
            long now = System.nanoTime();
            acc += (now - prev) / 1_000_000_000.0;
            prev = now;

            while (acc >= DT) {
                view.update((float) DT);
                acc -= DT;
            }
            view.render();
            // маленькая уступка CPU
            try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        }
    }
}
