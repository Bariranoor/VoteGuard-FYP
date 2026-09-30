# P0 rules patch — nomination form after closure

Apply these three edits to the deployed Firestore Rules only after the web and Android changes have been tested. Do not change the student-nomination checks.

The purpose is narrow: a nomination form that was formally closed must remain readable by a paired Android voting device and must be accepted by the election-start rule. Student nomination submissions must still require `formStatus == "finalized"`, so a closed form cannot accept new applications.

1. In `function hasFinalizedNominationForm(electionId)`, replace:

```rules
).data.formStatus == "finalized"
```

with:

```rules
).data.formStatus in ["finalized", "closed"]
```

2. In `match /Positions/{positionId}`, change only the `allow get` block whose preceding comment begins `A paired Android voting device may GET`. Replace its nomination-form status comparison with:

```rules
).data.formStatus in ["finalized", "closed"]
```

3. In `match /NominationForms/{formId}`, change only the `allow get` block whose preceding comment begins `Paired Android voting devices may read`. Replace:

```rules
resource.data.formStatus == "finalized"
```

with:

```rules
resource.data.formStatus in ["finalized", "closed"]
```

Do **not** change the public-student `allow get` or `NominationApplications` create check. Those must continue to require exactly `"finalized"`.
