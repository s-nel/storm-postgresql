package org.apache.storm.trident.state.postgresql;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.shade.com.google.common.collect.Lists;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.trident.TridentTopology;
import org.apache.storm.trident.fluent.GroupedStream;
import org.apache.storm.trident.operation.BaseFilter;
import org.apache.storm.trident.operation.CombinerAggregator;
import org.apache.storm.trident.operation.TridentCollector;
import org.apache.storm.trident.spout.IBatchSpout;
import org.apache.storm.trident.state.StateType;
import org.apache.storm.trident.state.postgresql.PostgresqlState;
import org.apache.storm.trident.state.postgresql.PostgresqlStateConfig;
import org.apache.storm.trident.tuple.TridentTuple;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

import clojure.lang.Numbers;

public class PostgresqlStateTopology {

	@SuppressWarnings("serial")
	static class RandomTupleSpout implements IBatchSpout {
		private transient Random random;
		private static final int BATCH = 5000;

		@Override
		@SuppressWarnings("rawtypes")
		public void open(final Map conf, final TopologyContext context) {
			random = new Random();
		}

		@Override
		public void emitBatch(final long batchId, final TridentCollector collector) {
			// emit a 3 number tuple (a,b,c)
			for (int i = 0; i < BATCH; i++) {
				collector.emit(new Values(random.nextInt(1000) + 1, random.nextInt(100) + 1, random.nextInt(100) + 1));
			}
		}

		@Override
		public void ack(final long batchId) {}

		@Override
		public void close() {}

		@Override
		@SuppressWarnings("rawtypes")
		public Map getComponentConfiguration() {
			return null;
		}

		@Override
		public Fields getOutputFields() {
			return new Fields("a", "b", "c");
		}
	}

	@SuppressWarnings("serial")
	static class ThroughputLoggingFilter extends BaseFilter {

		private static final Logger logger = Logger.getLogger(ThroughputLoggingFilter.class);
		private long count = 0;
		private Long start = System.nanoTime();
		private Long last = System.nanoTime();

		@Override
        public boolean isKeep(final TridentTuple tuple) {
			count += 1;
			final long now = System.nanoTime();
			if (now - last > 5000000000L) { // emit every 5 seconds
				logger.info("tuples per second = " + (count * 1000000000L) / (now - start));
				last = now;
			}
			return true;
		}
	}

	@SuppressWarnings("serial")
	static class CountSumSum implements CombinerAggregator<List<Number>> {

		@Override
		public List<Number> init(TridentTuple tuple) {
			return Lists.newArrayList(1L, (Number) tuple.getValue(0), (Number) tuple.getValue(1));
		}

		@Override
		public List<Number> combine(List<Number> val1, List<Number> val2) {
			return Lists.newArrayList(Numbers.add(val1.get(0), val2.get(0)), Numbers.add(val1.get(1), val2.get(1)), Numbers.add(val1.get(2), val2.get(2)));
		}

		@Override
		public List<Number> zero() {
			return Lists.newArrayList((Number) 0, (Number) 0, (Number) 0);
		}

	}

	/**
	 * storm local cluster executable
	 *
	 * @param args
	 */
	public static void main(final String[] args) {
		final String dburl = "jdbc:postgresql://localhost:5432/storm_test?user=dkatten";
		final TridentTopology topology = new TridentTopology();
		final GroupedStream stream = topology.newStream("test", new RandomTupleSpout()).each(new Fields(), new ThroughputLoggingFilter()).groupBy(new Fields("a"));
		final PostgresqlStateConfig config = new PostgresqlStateConfig();
		{
			config.setUrl(dburl);
			config.setTable("state");
			config.setKeyColumns(new String[]{"a"});
			config.setValueColumns(new String[]{"count","bsum","csum"});
			config.setType(StateType.NON_TRANSACTIONAL);
			config.setCacheSize(1000);
		}
		stream.persistentAggregate(PostgresqlState.newFactory(config), new Fields("b", "c"), new CountSumSum(), new Fields("sum"));

		final PostgresqlStateConfig config1 = new PostgresqlStateConfig();
		{
			config1.setUrl(dburl);
			config1.setTable("state_transactional");
			config1.setKeyColumns(new String[]{"a"});
			config1.setValueColumns(new String[]{"count","bsum","csum"});
			config1.setType(StateType.TRANSACTIONAL);
			config1.setCacheSize(1000);
		}
		stream.persistentAggregate(PostgresqlState.newFactory(config1), new Fields("b", "c"), new CountSumSum(), new Fields("sum"));

		final PostgresqlStateConfig config2 = new PostgresqlStateConfig();
		{
			config2.setUrl(dburl);
			config2.setTable("state_opaque");
			config2.setKeyColumns(new String[]{"a"});
			config2.setValueColumns(new String[]{"count","bsum","csum"});
			config2.setType(StateType.OPAQUE);
			config2.setCacheSize(1000);
		}
		stream.persistentAggregate(PostgresqlState.newFactory(config2), new Fields("b", "c"), new CountSumSum(), new Fields("sum"));

		final LocalCluster cluster = new LocalCluster();
		cluster.submitTopology("test", new Config(), topology.build());
		while (true) {

		}
	}
}
