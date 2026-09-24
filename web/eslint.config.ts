import eslint from '@eslint/js';
import angular from 'angular-eslint';
import { defineConfig } from 'eslint/config';
import cypress from 'eslint-plugin-cypress';
import prettier from 'eslint-plugin-prettier/recommended';
import globals from 'globals';
import tseslint from 'typescript-eslint';
// For a detailed explanation, visit: https://github.com/angular-eslint/angular-eslint/blob/main/docs/CONFIGURING_FLAT_CONFIG.md
// jhipster-needle-eslint-add-import - JHipster will add additional import here

export default defineConfig(
  {
    languageOptions: {
      globals: {
        ...globals.node,
      },
    },
  },
  // `.claude/` holds agent git worktrees — a second, complete copy of a checkout created INSIDE it
  // while an agent is mid-cycle. Without an ignore, `eslint .` walks that copy and every file in it
  // fails to parse:
  //
  //   Parsing error: "parserOptions.project" has been provided for @typescript-eslint/parser.
  //   The file was not found in any of the provided project(s): .claude/worktrees/…/probe.ts
  //
  // THE MECHANISM IS NOT THE ONE THE GLOB SUGGESTS. The typed block below is anchored at
  // `src/main/webapp/**/*.ts`, so planting a single `.ts` at that path does NOT reproduce it. What
  // does: ESLint resolves a config file PER LINTED FILE, searching upward from that file's own
  // directory, so the nested copy's OWN `eslint.config.ts` applies with the nested directory as its
  // basePath — against which the anchored glob matches perfectly well — while `parserOptions.project`
  // still resolves against `process.cwd()`, the outer root. The trigger is therefore **a config file
  // in a subdirectory**, not a worktree, and git is irrelevant.
  //
  // MEASURED HERE 2026-09-24, and the answer differs from the siblings' because the LAYOUT differs.
  // In `hc-admin/app` and `hc-vendor/web` the Angular app IS the repository root, so worktrees land
  // inside it and the hazard is live. Here `web/` is a subdirectory and worktrees land at
  // `<repo>/.claude/worktrees/`, a SIBLING of `web/` that `eslint .` never walks. Three probes, this
  // app, this config:
  //
  //   A  nothing planted                                     rc=0
  //   B  nested copy at <repo>/.claude/worktrees/zz/web      rc=0   <- today's real shape, UNAFFECTED
  //   C  nested copy at web/.claude/worktrees/zz             rc=1, 1 parse error
  //
  // So this entry is PROPHYLACTIC for the layout as it stands and a real fix for shape C — which is
  // what occurs the day anything roots a worktree, or any other checkout, at `web/`. It is one line,
  // and `pretest` runs `npm run lint`, so the failure it prevents is `npm test` dying before a single
  // test under a message naming a parser option rather than the nesting.
  { ignores: ['target/classes/static/', 'target/', 'src/main/webapp/swagger-ui/', 'dist/', '.claude/'] },
  eslint.configs.recommended,
  {
    files: ['**/*.{js,cjs,mjs}'],
    rules: {
      'no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    },
  },
  {
    files: ['src/main/webapp/**/*.ts'],
    extends: [...tseslint.configs.strictTypeChecked, ...tseslint.configs.stylistic, ...angular.configs.tsRecommended],
    languageOptions: {
      globals: {
        ...globals.browser,
      },
      parserOptions: {
        project: ['./tsconfig.app.json', './tsconfig.spec.json'],
      },
    },
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'abm',
          style: 'kebab-case',
        },
      ],
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'abm',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/relative-url-prefix': 'error',
      '@typescript-eslint/consistent-type-definitions': 'off',
      '@typescript-eslint/explicit-function-return-type': ['error', { allowExpressions: true }],
      '@typescript-eslint/explicit-module-boundary-types': 'off',
      '@typescript-eslint/member-ordering': [
        'error',
        {
          default: [
            'public-static-field',
            'protected-static-field',
            'private-static-field',
            'public-instance-field',
            'protected-instance-field',
            'private-instance-field',
            'constructor',
            'public-static-method',
            'protected-static-method',
            'private-static-method',
            'public-instance-method',
            'protected-instance-method',
            'private-instance-method',
          ],
        },
      ],
      '@typescript-eslint/no-confusing-void-expression': 'off',
      '@typescript-eslint/no-empty-object-type': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-extraneous-class': 'off',
      '@typescript-eslint/no-misused-spread': 'off',
      '@typescript-eslint/no-floating-promises': 'off',
      '@typescript-eslint/no-non-null-assertion': 'off',
      '@typescript-eslint/no-shadow': ['error'],
      '@typescript-eslint/no-unnecessary-condition': 'error',
      '@typescript-eslint/no-unnecessary-type-arguments': 'off',
      '@typescript-eslint/no-unsafe-argument': 'off',
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@typescript-eslint/no-unsafe-call': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
      '@typescript-eslint/prefer-nullish-coalescing': 'error',
      '@typescript-eslint/prefer-optional-chain': 'error',
      '@typescript-eslint/restrict-template-expressions': ['error', { allowNumber: true }],
      '@typescript-eslint/unbound-method': 'off',
      'arrow-body-style': 'error',
      curly: 'error',
      eqeqeq: ['error', 'always', { null: 'ignore' }],
      'guard-for-in': 'error',
      'no-bitwise': 'error',
      'no-caller': 'error',
      'no-console': ['error', { allow: ['warn', 'error'] }],
      'no-eval': 'error',
      'no-labels': 'error',
      'no-new': 'error',
      'no-new-wrappers': 'error',
      'object-shorthand': ['error', 'always', { avoidExplicitReturnArrows: true }],
      radix: 'error',
      'spaced-comment': ['warn', 'always'],
    },
  },
  {
    files: ['src/main/webapp/**/*.spec.ts'],
    rules: {
      '@typescript-eslint/no-empty-function': 'off',
    },
  },
  {
    files: ['src/main/webapp/**/*.html'],
    extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility],
    rules: {
      '@angular-eslint/template/click-events-have-key-events': 'off',
      '@angular-eslint/template/interactive-supports-focus': 'off',
    },
  },
  {
    files: ['src/test/javascript/cypress/**/*.ts'],
    extends: [...tseslint.configs.recommendedTypeChecked, cypress.configs.recommended],
    languageOptions: {
      parserOptions: {
        project: ['./src/test/javascript/cypress/tsconfig.json'],
      },
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unsafe-argument': 'off',
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@typescript-eslint/no-unsafe-call': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
      '@typescript-eslint/unbound-method': 'off',
    },
  },
  // jhipster-needle-eslint-add-config - JHipster will add additional config here
  {
    extends: [prettier],
  },
);
