package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.DocumentSystemOutput;
import com.bbn.kbp.events2014.DocumentSystemOutput2015;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Created by rgabbard on 4/22/14.
 */
public class TestKeepBestJustificationOnly {

  final Symbol docid = Symbol.from("doc");
  final Symbol type1 = Symbol.from("Type1");
  final Symbol type2 = Symbol.from("Type2");
  final Symbol role1 = Symbol.from("Role1");
  final Symbol role2 = Symbol.from("Role2");
  final KBPRealis realis1 = KBPRealis.Actual;
  final KBPRealis realis2 = KBPRealis.Generic;
  final KBPString CAS1 = KBPString.from("CAS1", 0, 1);
  final KBPString CAS1_different_offsets = KBPString.from("CAS1", 0, 2);
  final KBPString CAS2 = KBPString.from("CAS2", 0, 1);
  final CharOffsetSpan baseFiller1 = CharOffsetSpan.fromOffsetsOnly(42, 43);
  final CharOffsetSpan baseFiller2 = CharOffsetSpan.fromOffsetsOnly(52, 53);
  final Set<CharOffsetSpan> argumentJustifications1 = ImmutableSet.of();
  final Set<CharOffsetSpan> argumentJustifications2 = ImmutableSet.of(
      CharOffsetSpan.fromOffsetsOnly(0, 100));

  final Set<CharOffsetSpan> predicateJustifications1 = ImmutableSet.of(
      CharOffsetSpan.fromOffsetsOnly(98, 99));
  final Set<CharOffsetSpan> predicateJustifications2 = ImmutableSet.of(
      CharOffsetSpan.fromOffsetsOnly(70, 80));

  final Scored<Response> best = Scored.from(
      Response.of(docid, type1, role1, CAS1, baseFiller2,
          argumentJustifications1, predicateJustifications1, realis1), 0.9);

  // this differs from best only in base filler. It ties on score, but should
  // lose out to best by the tiebreaker based on hash code (response ID)
  final Scored<Response> tiesBestButLosesTiebreakByHash = Scored.from(
      Response.of(docid, type1, role1, CAS1, baseFiller1,
          argumentJustifications1, predicateJustifications1, realis1), 0.9);

  // these will also lose to best due to having a lower score
  final Scored<Response> differentPJWithLowerScore = Scored.from(
      Response.of(docid, type1, role1, CAS1, baseFiller1,
          argumentJustifications1, predicateJustifications2, realis1), 0.8);

  final Scored<Response> differentBFWithLowerScore = Scored.from(
      Response.of(docid, type1, role1, CAS1, baseFiller2,
          argumentJustifications1, predicateJustifications1, realis1), 0.8);

  final Scored<Response> differentAJWithLowerScore = Scored.from(
      Response.of(docid, type1, role1, CAS1, baseFiller1,
          argumentJustifications2, predicateJustifications1, realis1), 0.8);

  // all of the below shouldn't be in competition with anything else
  // due to unique (docid, type, role, CAS, Realis) tuples
  final Scored<Response> differByType = Scored.from(
      Response.of(docid, type2, role1, CAS1, baseFiller1,
          argumentJustifications1, predicateJustifications1, realis1), 0.9);

  final Scored<Response> differByRole = Scored.from(
      Response.of(docid, type2, role1, CAS1, baseFiller1,
          argumentJustifications1, predicateJustifications1, realis1), 0.9);

  final Scored<Response> differByRealis = Scored.from(
      Response.of(docid, type2, role1, CAS1, baseFiller1,
          argumentJustifications1, predicateJustifications1, realis1), 0.9);

  final Scored<Response> differByCASOffsets = Scored.from(
      Response.of(docid, type2, role1, CAS1_different_offsets, baseFiller1,
          argumentJustifications1, predicateJustifications1, realis1), 0.9);

  final Scored<Response> differByCASString = Scored.from(
      Response.of(docid, type2, role1, CAS2, baseFiller1,
          argumentJustifications1, predicateJustifications1, realis1), 0.9);

  final ArgumentOutput toDeduplicate = ArgumentOutput.from(docid, ImmutableList.of(
      best, tiesBestButLosesTiebreakByHash, differentPJWithLowerScore,
      differentBFWithLowerScore, differentAJWithLowerScore, differByType,
      differByRole, differByRealis, differByCASOffsets, differByCASString));


  @Test
  public void testKeepBestJustificationOnly() {
    final Symbol docid = Symbol.from("doc");
    final Symbol type1 = Symbol.from("Type1");
    final Symbol type2 = Symbol.from("Type2");
    final Symbol role1 = Symbol.from("Role1");
    final Symbol role2 = Symbol.from("Role2");
    final KBPRealis realis1 = KBPRealis.Actual;
    final KBPRealis realis2 = KBPRealis.Generic;
    final KBPString CAS1 = KBPString.from("CAS1", 0, 1);
    final KBPString CAS1_different_offsets = KBPString.from("CAS1", 0, 2);
    final KBPString CAS2 = KBPString.from("CAS2", 0, 1);
    final CharOffsetSpan baseFiller1 = CharOffsetSpan.fromOffsetsOnly(42, 43);
    final CharOffsetSpan baseFiller2 = CharOffsetSpan.fromOffsetsOnly(52, 53);
    final Set<CharOffsetSpan> argumentJustifications1 = ImmutableSet.of();
    final Set<CharOffsetSpan> argumentJustifications2 = ImmutableSet.of(
        CharOffsetSpan.fromOffsetsOnly(0, 100));
    final Set<CharOffsetSpan> argumentJustifications3 = ImmutableSet.of(
        CharOffsetSpan.fromOffsetsOnly(0, 110));

    final Set<CharOffsetSpan> predicateJustifications1 = ImmutableSet.of(
        CharOffsetSpan.fromOffsetsOnly(98, 99));
    final Set<CharOffsetSpan> predicateJustifications2 = ImmutableSet.of(
        CharOffsetSpan.fromOffsetsOnly(70, 80));

    final Scored<Response> best = Scored.from(
        Response.of(docid, type1, role1, CAS1, baseFiller2,
            argumentJustifications1, predicateJustifications1, realis1), 0.9);

    // this differs from best only in base filler. It ties on score, but should
    // lose out to best by the tiebreaker based on hash code (response ID)
    final Scored<Response> tiesBestButLosesTiebreakByHash = Scored.from(
        Response.of(docid, type1, role1, CAS1, baseFiller1,
            argumentJustifications1, predicateJustifications1, realis1), 0.9);

    // these will also lose to best due to having a lower score
    final Scored<Response> differentPJWithLowerScore = Scored.from(
        Response.of(docid, type1, role1, CAS1, baseFiller1,
            argumentJustifications1, predicateJustifications2, realis1), 0.8);

    final Scored<Response> differentBFWithLowerScore = Scored.from(
        Response.of(docid, type1, role1, CAS1, baseFiller2,
            argumentJustifications1, predicateJustifications1, realis1), 0.8);

    final Scored<Response> differentAJWithLowerScore = Scored.from(
        Response.of(docid, type1, role1, CAS1, baseFiller1,
            argumentJustifications2, predicateJustifications1, realis1), 0.8);

    final Scored<Response> anotherDifferentAJWithLowerScore = Scored.from(
        Response.of(docid, type1, role1, CAS1, baseFiller1,
            argumentJustifications3, predicateJustifications1, realis1), 0.7);

    // all of the below shouldn't be in competition with anything else
    // due to unique (docid, type, role, CAS, Realis) tuples
    final Scored<Response> differByType = Scored.from(
        Response.of(docid, type2, role1, CAS1, baseFiller1,
            argumentJustifications1, predicateJustifications1, realis1), 0.9);

    final Scored<Response> differByRole = Scored.from(
        Response.of(docid, type2, role1, CAS1, baseFiller1,
            argumentJustifications1, predicateJustifications1, realis1), 0.9);

    final Scored<Response> differByRealis = Scored.from(
        Response.of(docid, type2, role1, CAS1, baseFiller1,
            argumentJustifications1, predicateJustifications1, realis1), 0.9);

    final Scored<Response> differByCASOffsets = Scored.from(
        Response.of(docid, type2, role1, CAS1_different_offsets, baseFiller1,
            argumentJustifications1, predicateJustifications1, realis1), 0.9);

    final Scored<Response> differByCASString = Scored.from(
        Response.of(docid, type2, role1, CAS2, baseFiller1,
            argumentJustifications1, predicateJustifications1, realis1), 0.9);

    final ArgumentOutput toDeduplicate = ArgumentOutput.from(docid, ImmutableList.of(
        best, tiesBestButLosesTiebreakByHash, differentPJWithLowerScore,
        differentBFWithLowerScore, differentAJWithLowerScore, differByType,
        differByRole, differByRealis, differByCASOffsets, differByCASString,
        anotherDifferentAJWithLowerScore));


    // reference drops two losers
    final ArgumentOutput reference = ArgumentOutput.from(docid, ImmutableList.of(
        best, differByType,
        differByRole, differByRealis, differByCASOffsets, differByCASString,
        anotherDifferentAJWithLowerScore));
    final ResponseLinking responseLinking = ResponseLinking.builder().docID(docid)
        .responseSets(ImmutableSet.of(ResponseSet.from(best.item(), tiesBestButLosesTiebreakByHash.item(),
                differentPJWithLowerScore.item(),
                differentBFWithLowerScore.item(), differentAJWithLowerScore.item(),
                differByType.item(),
                differByRole.item(), differByRealis.item(), differByCASOffsets.item(),
                differByCASString.item()),
            ResponseSet.from(anotherDifferentAJWithLowerScore.item()))).build();

    final DocumentSystemOutput systemOutput = DocumentSystemOutput2015
        .from(toDeduplicate, responseLinking);
    final DocumentSystemOutput deduplicated =
        KeepBestJustificationOnly.asFunctionOnSystemOutput().apply(systemOutput);
    assertEquals(reference, deduplicated.arguments());
  }
}
