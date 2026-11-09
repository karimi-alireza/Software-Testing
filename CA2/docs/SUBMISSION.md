# Submission handoff

These actions come from page 3 of SWT-05-CA2.pdf. Local implementation and test
execution do not publish a repository or submit coursework. No Git repository
was present in the supplied folder, and no repository was created or published
by this work.

- [ ] Confirm the required two-person team and ensure both members can explain the tests.
- [ ] Create a private repository containing this completed project, or use the course's upstream fork workflow. Upstream link from the PDF: <https://github.com/UT-SWT/F05-CA2>.
- [ ] Keep the repository **Private through the end of ۱۴۰۵/۰۸/۲۳**; make it **Public afterward** so the teaching assistants can inspect it. Use course announcements for the exact submission time; the PDF does not specify a clock time or timezone.
- [ ] Run `mvn clean verify` from the repository root. Confirm zero failed/error/skipped tests. Review `docs/VERIFICATION.md` and generated coverage.
- [ ] Commit the finished source, tests, scripts, `pom.xml`, and documentation **before the submission deadline**. Exclude `target/` and local IDE files (already in `.gitignore`).
- [ ] Push the final commit and confirm that GitHub displays the same commit hash.
- [ ] Upload the GitHub repository URL and **full final commit hash** to eLearn. The PDF says submission by one team member is enough.
- [ ] Prepare for the virtual oral assessment. The PDF says most marks depend on explaining test concepts and behavior, and explicitly permits AI assistance while expecting understanding.
- [ ] Follow the course's originality and plagiarism rules.

After a Git repository exists and the final changes have been committed, inspect:

```sh
git status --short
git log -1 --format='%H %aI %cI'
git remote -v
```

The first command should show a clean working tree. The second gives the full
hash plus author/committer timestamps to check against the deadline. The third
helps identify the submission URL. Do not submit an uncommitted folder, a stale
hash, or a local filesystem path in place of the remote URL. Check that the final
hash is actually pushed; a local commit alone does not establish remote delivery.
