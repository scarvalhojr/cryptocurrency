import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Arrays;


public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {

        ArrayList<UTXO> claimed_utxo = new ArrayList<UTXO>();
        double total_value = 0;

        int tx_in_number = 0;
        for (Transaction.Input tx_in : tx.getInputs()) {

            UTXO origin_utxo = new UTXO(tx_in.prevTxHash, tx_in.outputIndex);

            if (claimed_utxo.contains(origin_utxo))
                // Origin transaction output already claimed
                return false;

            claimed_utxo.add(origin_utxo);

            Transaction.Output origin_tx_out = this.utxoPool.getTxOutput(origin_utxo);
            if (origin_tx_out == null)
                // Original transaction output no in the UTXO pool
                return false;

            total_value += origin_tx_out.value;

            if (!Crypto.verifySignature(origin_tx_out.address,
                                        tx.getRawDataToSign(tx_in_number),
                                        tx_in.signature))
                // Signature on transaction input did not verify
                return false;

            tx_in_number++;
        }

        for (Transaction.Output tx_out : tx.getOutputs()) {
            if (tx_out.value <= 0)
                // Negative transaction output value
                return false;
            total_value -= tx_out.value;
        }

        if (total_value < 0)
            // Negative transaction balance
            return false;

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {

        List<Transaction> pending = new ArrayList<Transaction>(Arrays.asList(possibleTxs));
        List<Transaction> processed = new ArrayList<Transaction>();
        boolean done;

        do {
            done = true;

            ListIterator<Transaction> iter = pending.listIterator();
            while(iter.hasNext()){
                Transaction tx = iter.next();

                if (isValidTx(tx)) {
                    for (Transaction.Input tx_in : tx.getInputs())
                        utxoPool.removeUTXO(new UTXO(tx_in.prevTxHash, tx_in.outputIndex));

                    int index = 0;
                    for (Transaction.Output tx_out : tx.getOutputs()) {
                        utxoPool.addUTXO(new UTXO(tx.getHash(), index), tx_out);
                        index++;
                    }

                    // when a new transaction is processed, we need to remove
                    // it from pending list, add it to processed list, and
                    // later revist remaining pending transactions again as
                    // some may have become valid
                    processed.add(tx);
                    iter.remove();
                    done = false;
                }
            }
        } while (!done);

        Transaction[] accepted = new Transaction[processed.size()];
        return processed.toArray(accepted);
    }

}
