import java.util.concurrent.*;

public class IdcDm {
    private final static int QUEUE_CAPACITY = 16;
    /**
     * Receive arguments from the command-line, provide some feedback and start the download.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int numberOfWorkers = 1;
        Long maxBytesPerSecond = null;

        if (args.length < 1 || args.length > 3) {
            System.err.printf("usage:\n\tjava IdcDm URL [MAX-CONCURRENT-CONNECTIONS] [MAX-DOWNLOAD-LIMIT]\n");
            System.exit(1);
        } else if (args.length >= 2) {
            numberOfWorkers = Integer.parseInt(args[1]);
            if (args.length == 3)
                maxBytesPerSecond = Long.parseLong(args[2]);
        }

        String url = args[0];

        System.err.printf("Downloading");
        if (numberOfWorkers > 1)
            System.err.printf(" using %d connections", numberOfWorkers);
        if (maxBytesPerSecond != null)
            System.err.printf(" limited to %d Bps", maxBytesPerSecond);
        System.err.printf("...\n");

        DownloadURL(url, numberOfWorkers, maxBytesPerSecond);
    }

    /**
     * Initiate the file's metadata, and iterate over missing ranges. For each:
     * 1. Setup the Queue, TokenBucket, DownloadableMetadata, FileWriter, RateLimiter, and a pool of HTTPRangeGetters
     * 2. Join the HTTPRangeGetters, send finish marker to the Queue and terminate the TokenBucket
     * 3. Join the FileWriter and RateLimiter
     *
     * Finally, print "Download succeeded/failed" and delete the metadata as needed.
     *
     * @param url URL to download
     * @param numberOfWorkers number of concurrent connections
     * @param maxBytesPerSecond limit on download bytes-per-second
     */
    private static void DownloadURL(String url, int numberOfWorkers, Long maxBytesPerSecond) {
        TokenBucket tokenBucket = new TokenBucket();
        //BlockingQueue<Range> rangeQueue = new ArrayBlockingQueue<Range>(QUEUE_CAPACITY);
        BlockingQueue<Chunk> chunkQueue = new ArrayBlockingQueue<Chunk>(QUEUE_CAPACITY);
        DownloadableMetadata downloadedMetaFile = new DownloadableMetadata(url);

        //new rate limiter thread
        Thread rateLimiter = new Thread(new RateLimiter(tokenBucket,maxBytesPerSecond));

        //new file writer thread
        Thread fileWriter = new Thread(new FileWriter(downloadedMetaFile, chunkQueue));
        fileWriter.start();

        //initiate thread pool
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(numberOfWorkers);
        //initiate scheduled thread calling printPrecentageLeft() every 5 seconds
        executor.scheduleAtFixedRate(()-> {
            downloadedMetaFile.printPrecentageLeft();
        },0,5000L,TimeUnit.MILLISECONDS);
        rateLimiter.run();
        for (int i = 0; i < numberOfWorkers; i++){
            Thread worker = new Thread(new HTTPRangeGetter
                    (url ,downloadedMetaFile.getMissingRange(),chunkQueue,tokenBucket));
            executor.execute(worker);
        }

        //join fileWriter
        try{
            fileWriter.join();
            executor.shutdown();
            while (!executor.isTerminated()){}
        }catch (InterruptedException e) {
            e.printStackTrace();
        }

        //stop rate limiter. not sure if it is working the way it should...
        rateLimiter.interrupt();


    }
}
