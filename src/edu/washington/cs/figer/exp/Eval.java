package edu.washington.cs.figer.exp;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;

import edu.washington.cs.figer.data.Label;
import edu.washington.cs.figer.ml.LogisticRegression;
import edu.washington.cs.figer.ml.Model;
import edu.washington.cs.figer.util.Debug;
import edu.washington.cs.figer.util.FileUtil;
import edu.washington.cs.figer.util.X;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

public class Eval {
	public static String trueLabelFile = "true.label2";

	public static MultiLabelNERPerf process(String file) {
		Model model = new LogisticRegression();
		MultiLabelNERPerf perf = new MultiLabelNERPerf(model);
		try {
			int[] length = new int[] { 622, 573, 1161, 663, 701, 1083, 1085,
					889, 818, 692, 150, 382, 383, 101, 254, 219, 252, 414 };
			int[] starts = new int[length.length];
			int[] skipDocs = new int[] {};
			TIntSet skipLines = new TIntHashSet();
			{
				for (int i = 1; i < starts.length; i++) {
					starts[i] = starts[i - 1] + length[i - 1];
				}
				for (int i = 0; i < skipDocs.length; i++) {
					for (int j = starts[skipDocs[i]]; j < starts[skipDocs[i]]
							+ length[skipDocs[i]]; j++) {
						skipLines.add(j);
					}
				}
			}

			String[] lines = FileUtil.getTextFromFile(X.get("tagset")).split(
					"\n");
			for (String line : lines) {
				String arg = line.substring(0, line.indexOf("\t")), newType = line
						.substring(line.indexOf("\t") + 1).trim()
						.replace("\t", "/");
				model.labelFactory.getLabel(newType);
			}

			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(file))), reader2 = new BufferedReader(
					new InputStreamReader(new FileInputStream(trueLabelFile)));
			String line = null;
			Hashtable<String, String> pred = new Hashtable<String, String>(), truth = new Hashtable<String, String>();
			String pTag = null, tTag = null;
			int ps = 0, ts = 0;
			int pe = 0, te = 0;
			int l = 0;
			int lineno = 0;
			HashMap<String, Integer> res = new HashMap<String, Integer>();
			StringBuilder sent = new StringBuilder();
			while ((line = reader.readLine()) != null) {
				if (skipLines.contains(lineno)) {
					line = reader2.readLine();
					lineno++;
					continue;
				}
				if (!line.trim().equals("")) {
					// prediction
					String[] items = line.split("\t");
					if (items[1].startsWith("B-")) {
						if (pTag != null) {
							pred.put(ps + "\t" + pe, pTag);
						}
						ps = l;
						pe = ps + 1;
						pTag = items[1].substring(2);
					} else if (items[1].startsWith("I-")) {
						pe = l;
					} else {
						if (pTag != null) {
							pred.put(ps + "\t" + pe, pTag);
							pTag = null;
						}
					}
					sent.append(items[0] + " ");

					// true
					line = reader2.readLine();
					items = line.split("\t");
					if (items[1].startsWith("B-")) {
						if (tTag != null) {
							truth.put(ts + "\t" + te, tTag);
						}
						ts = l;
						te = ts + 1;
						tTag = items[1].substring(2);
					} else if (items[1].startsWith("I-")) {
						te = l;
					} else {
						if (tTag != null) {
							truth.put(ts + "\t" + te, tTag);
							tTag = null;
						}
					}

					l++;
				} else {
					for (String str : truth.keySet()) {
						ArrayList<Label> tlabels = new ArrayList<Label>();
						for (String label : truth.get(str).split(",")) {
							tlabels.add(model.labelFactory.getLabel(label));
						}
						ArrayList<Label> plabels = new ArrayList<Label>();
						if (pred.containsKey(str)) {
							for (String label : pred.get(str).split(",")) {
								plabels.add(model.labelFactory.getLabel(label));
							}
						}
						Debug.vpl(sent.toString() + "\t" + str);
						Debug.vpl("True labels:\t" + tlabels);
						Debug.vpl("Pred labels:\t" + plabels);
						perf.update(plabels, tlabels);
						{
							boolean eq = true;
							if (plabels.size() == tlabels.size()) {
								for (Label ll : plabels) {
									if (!tlabels.contains(ll)) {
										eq = false;
										break;
									}
								}
							} else {
								eq = false;
							}
							if (eq) {
								res.put(lineno + "\t" + str, 1);
							} else {
								res.put(lineno + "\t" + str, 0);
							}
						}
					}
					for (String str : pred.keySet()) {
						if (!truth.containsKey(str)) {
							ArrayList<Label> plabels = new ArrayList<Label>();
							for (String label : pred.get(str).split(",")) {
								plabels.add(model.labelFactory.getLabel(label));
							}
							ArrayList<Label> tlabels = new ArrayList<Label>();
							perf.update(plabels, tlabels);
						}
					}
					line = reader2.readLine();
					pred.clear();
					truth.clear();
					pTag = null;
					tTag = null;
					l = 0;
					sent = new StringBuilder();
				}

				lineno++;
			}

			for (String key : res.keySet()) {
				Debug.vpl(key + "\t" + res.get(key));
			}
			reader2.close();
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return perf;
	}
}
