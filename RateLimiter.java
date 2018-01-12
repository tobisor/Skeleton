import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A token bucket based rate-limiter.
 * <p>
 * This class should implement a "soft" rate limiter by adding maxBytesPerSecond tokens to the bucket every second,
 * or a "hard" rate limiter by resetting the bucket to maxBytesPerSecond tokens every second.
 */
public class RateLimiter implements Runnable {
    private final TokenBucket tokenBucket;
    private final Long maxBytesPerSecond;
    private LimiterMethod limiterMethod;

    RateLimiter(TokenBucket tokenBucket, Long maxBytesPerSecond) {
        this.tokenBucket = tokenBucket;
        this.maxBytesPerSecond = maxBytesPerSecond;
        limiterMethod = LimiterMethod.soft;
    }

    public void setLimiterMethod(LimiterMethod method) {
        limiterMethod = method;
    }


    @Override
    public void run() {

        while (Thread.interrupted()) {
            try {
                Thread.sleep(1000L);  //adding maxBps to token bucket every second 
                if (limiterMethod == LimiterMethod.hard) {
                    tokenBucket.set(maxBytesPerSecond);
                } else {
                    tokenBucket.add(maxBytesPerSecond);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();

            }
        }
    }

    public void kill() {
        Thread.currentThread().interrupt();
    }

    public enum LimiterMethod {
        soft,
        hard
    }
}
