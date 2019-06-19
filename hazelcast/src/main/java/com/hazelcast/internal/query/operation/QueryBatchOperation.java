package com.hazelcast.internal.query.operation;

import com.hazelcast.internal.query.QueryId;
import com.hazelcast.internal.query.QueryService;
import com.hazelcast.internal.query.io.Row;
import com.hazelcast.internal.query.io.RowBatch;
import com.hazelcast.internal.query.worker.data.BatchDataTask;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;

import java.io.IOException;
import java.util.List;

/**
 * Execution batch.
 */
public class QueryBatchOperation extends QueryAbstractOperation {
    private QueryId queryId;
    private int edgeId;
    private int sourceStripe;
    private int sourceThread;
    private int targetStripe;
    private int targetThread;
    private RowBatch batch;

    public QueryBatchOperation() {
        // No-op.
    }

    public QueryBatchOperation(
        QueryId queryId,
        int edgeId,
        int sourceStripe,
        int sourceThread,
        int targetStripe,
        int targetThread,
        RowBatch batch
    ) {
        this.queryId = queryId;
        this.edgeId = edgeId;
        this.sourceStripe = sourceStripe;
        this.sourceThread = sourceThread;
        this.targetStripe = targetStripe;
        this.targetThread = targetThread;
        this.batch = batch;
    }

    // TODO: Metadata.

    @Override
    public void run() throws Exception {
        QueryService service = getService();

        service.onQueryBatchRequest(new BatchDataTask(queryId, edgeId, sourceStripe, sourceThread,
            targetStripe, targetThread, batch));
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        out.writeObject(queryId);
        out.writeInt(edgeId);
        out.writeInt(sourceStripe);
        out.writeInt(sourceThread);
        out.writeInt(targetStripe);
        out.writeInt(targetThread);
        out.writeObject(batch);
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        queryId = in.readObject();
        edgeId = in.readInt();
        sourceStripe = in.readInt();
        sourceThread = in.readInt();
        targetStripe = in.readInt();
        targetThread = in.readInt();
        batch = in.readObject();
    }
}