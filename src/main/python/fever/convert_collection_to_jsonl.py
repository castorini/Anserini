# -*- coding: utf-8 -*-
"""
Anserini: A Lucene toolkit for replicable information retrieval research

Licensed under the Apache License, Version 2.0 (the 'License');
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an 'AS IS' BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

import json
import os
import argparse
"""
The name of this file is a bit misleading since the original FEVER dataset is
also in JSONL format. This script converts them into a JSONL format compatible
with anserini.
"""

def convert_collection(args):
    print('Converting collection...')

    files = os.listdir(args.collection_path)
    file_index = 0
    doc_index = 0
    for file in files:
        with open(os.path.join(args.collection_path, file), encoding='utf-8') as f:
            for line in f:
                doc = json.loads(line.strip())
                if doc['text']:  # only include non-empty wiki pages
                    if doc_index % args.max_docs_per_file == 0:
                        if doc_index > 0:
                            output_jsonl_file.close()
                        output_path = os.path.join(args.output_folder, f'docs{file_index:02d}.json')
                        output_jsonl_file = open(output_path, 'w', encoding='utf-8', newline='\n')
                        file_index += 1

                    output_dict = {'id': doc_index, 'contents': doc['text']}
                    output_jsonl_file.write(json.dumps(output_dict) + '\n')
                    doc_index += 1

                    if doc_index % 100000 == 0:
                        print('Converted {} docs in {} files'.format(doc_index, file_index))

    output_jsonl_file.close()

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Converts FEVER\'s jsonl collection to anserini jsonl files.')
    parser.add_argument('--collection_path', required=True, help='FEVER wiki-pages directory.')
    parser.add_argument('--output_folder', required=True, help='Output directory.')
    parser.add_argument('--max_docs_per_file',
                        default=1000000,
                        type=int,
                        help='Maximum number of documents in each jsonl file.')
    args = parser.parse_args()

    if not os.path.exists(args.output_folder):
        os.makedirs(args.output_folder)

    convert_collection(args)

    print('Done!')
